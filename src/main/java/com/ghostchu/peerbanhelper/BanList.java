package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import inet.ipaddr.IPAddress;
import inet.ipaddr.format.util.DualIPv4v6AssociativeTries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Component
public class BanList {
    private final AtomicReference<DualIPv4v6AssociativeTries<BanMetadata>> delegate = new AtomicReference<>(new DualIPv4v6AssociativeTries<>());
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Nullable
    public BanMetadata add(@NotNull PeerAddress address, @NotNull BanMetadata metadata) {
        try {
            lock.writeLock().lock();
            return delegate.get().put(address.getAddress(), metadata);
        } finally {
            lock.writeLock().unlock();
        }

    }

    public boolean remove(@NotNull PeerAddress address) {
        try {
            lock.writeLock().lock();
            return delegate.get().remove(address.getAddress());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean remove(@NotNull IPAddress address) {
        try {
            lock.writeLock().lock();
            return delegate.get().remove(address);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        try {
            lock.readLock().lock();
            return delegate.get().size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @NotNull
    public DualIPv4v6AssociativeTries<BanMetadata> copy() {
        try {
            lock.writeLock().lock();
            return delegate.get().clone();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void directAccess(boolean write, Consumer<DualIPv4v6AssociativeTries<BanMetadata>> consumer) {
        try {
            if (write) {
                lock.writeLock().lock();
            } else {
                lock.readLock().lock();
            }
            consumer.accept(delegate.get());
        } finally {
            if (write) {
                lock.writeLock().unlock();
            } else {
                lock.readLock().unlock();
            }
        }

    }

    @NotNull
    public Set<IPAddress> copyKeySet() {
        Set<IPAddress> ipAddresses = new HashSet<>();
        try {
            lock.readLock().lock();
            delegate.get().forEach(ipAddresses::add);
        } finally {
            lock.readLock().unlock();
        }
        return ipAddresses;
    }

    public boolean contains(@NotNull PeerAddress address) {
        try {
            lock.readLock().lock();
            return delegate.get().elementContains(address.getAddress());
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean contains(@NotNull IPAddress address) {
        try {
            lock.readLock().lock();
            return delegate.get().elementContains(address);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Nullable
    public BanMetadata get(@NotNull PeerAddress address) {
        try {
            lock.readLock().lock();
            return delegate.get().elementsContaining(address.getAddress()).getValue();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Nullable
    public BanMetadata get(@NotNull IPAddress address) {
        try {
            lock.readLock().lock();
            return delegate.get().elementsContaining(address).getValue();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void addAll(@NotNull Map<IPAddress, BanMetadata> map) {
        try {
            lock.writeLock().lock();
            map.forEach(delegate.get()::put);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void forEach(@NotNull BiConsumer<? super IPAddress, ? super BanMetadata> action) {
        try {
            lock.readLock().lock();
            delegate.get().forEach(ip -> {
                var value = delegate.get().get(ip);
                action.accept(ip, value);
            });
        } finally {
            lock.readLock().unlock();
        }
    }

    public void reset() {
        try {
            lock.writeLock().lock();
            delegate.set(new DualIPv4v6AssociativeTries<>());
        } finally {
            lock.writeLock().unlock();
        }
    }
}
