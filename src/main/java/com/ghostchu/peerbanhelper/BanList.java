package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import inet.ipaddr.IPAddress;
import inet.ipaddr.format.util.AssociativeAddressTrie;
import inet.ipaddr.format.util.DualIPv4v6AssociativeTries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Component
public class BanList {
    private final DualIPv4v6AssociativeTries<BanMetadata> delegate = new DualIPv4v6AssociativeTries<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @NotNull
    public Map<IPAddress, BanMetadata> toMap() {
        Map<IPAddress, BanMetadata> map = new HashMap<>();
        try {
            lock.readLock().lock();
            delegate.forEach(ip -> map.put(ip, delegate.get(ip)));
        } finally {
            lock.readLock().unlock();
        }
        return map;
    }

    @Nullable
    public BanMetadata add(@NotNull PeerAddress address, @NotNull BanMetadata metadata) {
        try {
            lock.writeLock().lock();
            return delegate.put(address.getAddress().toPrefixBlock(), metadata);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Nullable
    public AssociativeAddressTrie.AssociativeTrieNode<? extends IPAddress, BanMetadata> remove(@NotNull PeerAddress address) {
       return remove(address.getAddress());
    }

    @Nullable
    public AssociativeAddressTrie.AssociativeTrieNode<? extends IPAddress, BanMetadata> remove(@NotNull IPAddress address) {
        try {
            lock.writeLock().lock();
            return delegate.removeElementsContainedBy(address);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        try {
            lock.readLock().lock();
            return delegate.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @NotNull
    public DualIPv4v6AssociativeTries<BanMetadata> copy() {
        try {
            lock.writeLock().lock();
            return delegate.clone();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void directAccess(boolean writeLock, @NotNull Consumer<DualIPv4v6AssociativeTries<BanMetadata>> consumer) {
        try {
            if (writeLock) {
                lock.writeLock().lock();
            } else {
                lock.readLock().lock();
            }
            consumer.accept(delegate);
        } finally {
            if (writeLock) {
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
            delegate.forEach(ipAddresses::add);
        } finally {
            lock.readLock().unlock();
        }
        return ipAddresses;
    }

    @NotNull
    public List<IPAddress> copyValues() {
        List<IPAddress> ipAddresses = new ArrayList<>();
        try {
            lock.readLock().lock();
            delegate.forEach(ipAddresses::add);
        } finally {
            lock.readLock().unlock();
        }
        return ipAddresses;
    }

    public boolean contains(@NotNull PeerAddress address) {
        try {
            lock.readLock().lock();
            return delegate.elementContains(address.getAddress());
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean contains(@NotNull IPAddress address) {
        try {
            lock.readLock().lock();
            return delegate.elementContains(address);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Nullable
    public BanMetadata get(@NotNull PeerAddress address) {
        try {
            lock.readLock().lock();
            var obj = delegate.elementsContaining(address.getAddress());
            return obj == null ? null : obj.getValue();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Nullable
    public BanMetadata get(@NotNull IPAddress address) {
        try {
            lock.readLock().lock();
            var obj = delegate.elementsContaining(address);
            return obj == null ? null : obj.getValue();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void addAll(@NotNull Map<IPAddress, BanMetadata> map) {
        try {
            lock.writeLock().lock();
            map.forEach((k,v)-> delegate.put(k.toPrefixBlock(), v));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void forEach(@NotNull BiConsumer<? super IPAddress, ? super BanMetadata> action) {
        try {
            lock.readLock().lock();
            delegate.forEach(ip -> {
                var value = delegate.get(ip);
                action.accept(ip, value);
            });
        } finally {
            lock.readLock().unlock();
        }
    }

    public void reset() {
        try {
            lock.writeLock().lock();
            delegate.getIPv4Trie().clear();
            delegate.getIPv6Trie().clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void readStream(@NotNull Consumer<Stream<AssociativeAddressTrie.AssociativeTrieNode<? extends IPAddress, BanMetadata>>> streamConsumer) {
        try {
            lock.readLock().lock();
            var spliterator = Spliterators.spliteratorUnknownSize(delegate.nodeIterator(false), 0);
            var stream = StreamSupport.stream(spliterator, false);
            streamConsumer.accept(stream);
        } finally {
            lock.readLock().unlock();
        }
    }
}
