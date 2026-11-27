package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.util.iocache.PBHCache;

import java.net.InetAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

// Used by one worker for one bounded group of peers. All-hit groups issue no SQL.
public final class PeerBatchLookup<K, V> {
    public static final int BATCH_SIZE = 32;
    private final PBHCache<K, V> cache;
    private final Supplier<List<K>> keys;
    private final Function<Set<K>, Map<K, V>> select;
    private Set<K> requested;
    private Map<K, V> rows;
    private long generation;

    public PeerBatchLookup(PBHCache<K, V> cache, Supplier<List<K>> keys, Function<Set<K>, Map<K, V>> select) {
        this.cache = cache;
        this.keys = keys;
        this.select = select;
    }

    public V load(K key, Supplier<V> single) {
        long current = cache.getFlushGeneration();
        if (rows == null || generation != current) {
            requested = new HashSet<>();
            for (K candidate : keys.get()) {
                if (!cache.asMap().containsKey(candidate)) requested.add(candidate);
            }
            requested.add(key);
            generation = current;
            rows = select.apply(requested);
        }
        if (requested.remove(key)) return rows.get(key);
        // A hit at the time of the query may since have been evicted.
        return single.get();
    }

    public record Endpoint(InetAddress address, int port) {
        public static Endpoint of(Peer peer) {
            return new Endpoint(peer.getPeerAddress().getAddress().toInetAddress(), peer.getPeerAddress().getPort());
        }
    }

    public static <E> LambdaQueryWrapper<E> matching(LambdaQueryWrapper<E> query, Collection<Endpoint> endpoints,
                                                      SFunction<E, InetAddress> address, SFunction<E, Integer> port) {
        return query.and(group -> endpoints.forEach(endpoint ->
                group.or(row -> row.eq(address, endpoint.address()).eq(port, endpoint.port()))));
    }
}
