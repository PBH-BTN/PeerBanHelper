package com.ghostchu.peerbanhelper.util.iocache;

import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.Pair;
import com.google.common.cache.*;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Slf4j
public class PBHCache<K, V> implements RemovalListener<K, V>, AutoCloseable, Cache<K,V> {
    private final Cache<@NotNull K, @NotNull V> delegate;
    private final @Nullable Consumer<Stream<Pair<@NotNull K, @NotNull V>>> removeCallback;
    private final AtomicBoolean receiveNotification = new AtomicBoolean(true);
    private final Queue<Pair<@NotNull K, @NotNull V>> pendingRemovals = new ConcurrentLinkedQueue<>();
    private final ScheduledFuture<?> cleanupTask;
    private final ScheduledFuture<?> callbackTask;

    public PBHCache(@Nullable Integer maxCapacity, @Nullable Long expireWriteMillis, @Nullable Long expireAccessMillis,
                    boolean weakKey, boolean weakValue, boolean softValue,
                    @Nullable Consumer<Stream<Pair<@NotNull K, @NotNull V>>> onRemove) {
        this.removeCallback = onRemove;
        var builder = CacheBuilder.newBuilder();
        if (maxCapacity != null) {
            builder.maximumSize(maxCapacity);
        }
        if (expireWriteMillis != null) {
            builder.expireAfterWrite(expireWriteMillis, TimeUnit.MILLISECONDS);
        }
        if (expireAccessMillis != null) {
            builder.expireAfterAccess(expireAccessMillis, TimeUnit.MILLISECONDS);
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
        this.callbackTask = CommonUtil.getScheduler().scheduleAtFixedRate(this::flushPendingRemovals, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public void onRemoval(@SuppressWarnings("NullableProblems") @NotNull RemovalNotification<K, V> notification) {
        if (!receiveNotification.get()) return;
        var key = notification.getKey();
        var value = notification.getValue();
        if (key == null) return;
        if (value == null) return;
        if (removeCallback == null) return;
        pendingRemovals.add(Pair.of(key, value));
    }

    private void flushPendingRemovals() {
        if (removeCallback == null) return;
        var batched = new ArrayList<Pair<@NotNull K, @NotNull V>>();
        Pair<@NotNull K, @NotNull V> pair;
        while ((pair = pendingRemovals.poll()) != null) {
            batched.add(pair);
        }
        if (batched.isEmpty()) return;
        try {
            removeCallback.accept(batched.stream());
        } catch (Exception e) {
            log.error("Failed to process batched cache removal callback", e);
        }
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
                .filter(entry->entry.getKey() != null && entry.getValue() != null)
                .map(entry->Pair.of(entry.getKey(), entry.getValue())));
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
