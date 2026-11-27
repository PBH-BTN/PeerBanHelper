package com.ghostchu.peerbanhelper.util.iocache;

import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.Pair;
import com.google.common.cache.*;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Slf4j
public class PBHCache<K, V> implements RemovalListener<K, V>, AutoCloseable, Cache<K, V> {
    private final Cache<@NotNull K, @NotNull V> delegate;
    private final @Nullable Consumer<Stream<Pair<@NotNull K, @NotNull V>>> removeCallback;
    private final AtomicBoolean receiveNotification = new AtomicBoolean(true);
    private final Map<K, V> pendingRemovals = new LinkedHashMap<>();
    private long flushGeneration;
    private final int pendingLimit;
    private final ScheduledFuture<?> cleanupTask;
    private final ScheduledFuture<?> callbackTask;

    public PBHCache(@Nullable Integer maxCapacity, @Nullable Long expireWriteMillis, @Nullable Long expireAccessMillis,
                    boolean weakKey, boolean weakValue, boolean softValue,
                    @Nullable Consumer<Stream<Pair<@NotNull K, @NotNull V>>> onRemove) {
        this.removeCallback = onRemove;
        pendingLimit = maxCapacity == null ? Integer.MAX_VALUE : Math.max(1, maxCapacity);
        var builder = CacheBuilder.newBuilder();
        if (maxCapacity != null) {
            builder.maximumSize(maxCapacity);
        }
        if (expireWriteMillis != null) {
            builder.expireAfterWrite(Duration.ofMillis(expireWriteMillis));
        }
        if (expireAccessMillis != null) {
            builder.expireAfterAccess(Duration.ofMillis(expireAccessMillis));
        }
        if (weakKey) {
            builder.weakKeys();
        }
        if (weakValue) {
            builder.weakValues();
        }
        if (softValue) {
            builder.softValues();
        }
        builder.removalListener(this);
        this.delegate = builder.build();
        this.cleanupTask = CommonUtil.getScheduler().scheduleAtFixedRate(delegate::cleanUp, 5, 5, TimeUnit.MINUTES);
        this.callbackTask = CommonUtil.getScheduler().scheduleAtFixedRate(this::scheduledFlush, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void onRemoval(@SuppressWarnings("NullableProblems") @NotNull RemovalNotification<K, V> notification) {
        if (!receiveNotification.get()) return;
        var key = notification.getKey();
        var value = notification.getValue();
        if (key == null) return;
        if (value == null) return;
        if (removeCallback == null) return;
        pendingRemovals.put(key, value);
    }

    private void scheduledFlush() {
        try {
            flushPendingRemovals();
        } catch (RuntimeException e) {
            log.error("Failed to persist evicted entries, will retry", e);
        }
    }

    private synchronized void flushPendingRemovals() {
        if (removeCallback == null || pendingRemovals.isEmpty()) return;
        removeCallback.accept(pendingRemovals.entrySet().stream().map(entry -> Pair.of(entry.getKey(), entry.getValue())));
        pendingRemovals.clear();
        flushGeneration++;
    }

    public synchronized long getFlushGeneration() {
        return flushGeneration;
    }

    // Write-back clients hold absolute state. Recover an evicted value until its transaction commits.
    public V getPendingOrLoad(K key, Callable<? extends V> loader) throws ExecutionException {
        return delegate.get(key, () -> {
            synchronized (this) {
                V pending = pendingRemovals.remove(key);
                if (pending != null) return pending;
                // Apply backpressure on misses while the database is unable to accept evictions.
                if (pendingRemovals.size() >= pendingLimit) flushPendingRemovals();
                return loader.call();
            }
        });
    }

    @Override
    public void close() throws Exception {
        receiveNotification.set(false);
        if (cleanupTask != null) {
            if (!cleanupTask.isCancelled()) {
                cleanupTask.cancel(false);
            }
        }
        if (callbackTask != null) {
            if (!callbackTask.isCancelled()) {
                callbackTask.cancel(false);
            }
        }
        flushPendingRemovals();
        if (removeCallback == null) return;
        //noinspection ConstantValue
        removeCallback.accept(delegate.asMap().entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .map(entry -> Pair.of(entry.getKey(), entry.getValue())));
    }

    @Override
    public @Nullable V getIfPresent(Object key) {
        return delegate.getIfPresent(key);
    }

    @Override
    public V get(K key, Callable<? extends V> loader) throws ExecutionException {
        return delegate.get(key, loader);
    }

    @Override
    public ImmutableMap<K, V> getAllPresent(Iterable<?> keys) {
        return delegate.getAllPresent(keys);
    }

    @Override
    public void put(K key, V value) {
        delegate.put(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        delegate.putAll(m);
    }

    @Override
    public void invalidate(Object key) {
        delegate.invalidate(key);
    }

    @Override
    public void invalidateAll(Iterable<?> keys) {
        delegate.invalidateAll(keys);
    }

    @Override
    public void invalidateAll() {
        delegate.invalidateAll();
    }

    @Override
    public long size() {
        return delegate.size();
    }

    @Override
    public CacheStats stats() {
        return delegate.stats();
    }

    @Override
    public ConcurrentMap<K, V> asMap() {
        return delegate.asMap();
    }

    @Override
    public void cleanUp() {
        delegate.cleanUp();
    }
}
