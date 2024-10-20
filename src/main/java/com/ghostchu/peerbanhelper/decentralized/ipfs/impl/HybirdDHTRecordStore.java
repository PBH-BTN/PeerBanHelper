package com.ghostchu.peerbanhelper.decentralized.ipfs.impl;

import com.ghostchu.peerbanhelper.database.dao.impl.DHTRecordDao;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import io.ipfs.multihash.Multihash;
import org.peergos.protocol.dht.RecordStore;
import org.peergos.protocol.ipns.IpnsRecord;
import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class HybirdDHTRecordStore implements RecordStore {
    private final Deque<DHTRecordDao.PersistTask> persistTasks = new ConcurrentLinkedDeque<>();
    private final Cache<Multihash, IpnsRecord> records = CacheBuilder
            .newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .maximumSize(200)
            .removalListener(notification -> {
                if (notification.getCause() != RemovalCause.EXPLICIT) {
                    Multihash key = (Multihash) notification.getKey();
                    IpnsRecord value = (IpnsRecord) notification.getValue();
                    persistTasks.offer(new DHTRecordDao.PersistTask(false, key, value));
                }
            })
            .build();
    private final DHTRecordDao dhtRecordDao;
    private final ScheduledExecutorService scheduled;

    public HybirdDHTRecordStore(DHTRecordDao dhtRecordDao) {
        this.dhtRecordDao = dhtRecordDao;
        this.scheduled = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
        this.scheduled.scheduleWithFixedDelay(this::flush, 5, 5, TimeUnit.MINUTES);
    }

    @Override
    public void put(Multihash multihash, IpnsRecord ipnsRecord) {
        records.put(multihash, ipnsRecord);
    }

    @Override
    public Optional<IpnsRecord> get(Multihash multihash) {
        var record = records.getIfPresent(multihash);
        if (record == null) {
            record = dhtRecordDao.get(multihash).orElse(null);
        }
        if (record != null) {
            records.put(multihash, record);
        }
        return Optional.ofNullable(record);
    }

    @Override
    public void remove(Multihash multihash) {
        //dhtRecordDao.remove(multihash);
        persistTasks.offer(new DHTRecordDao.PersistTask(true, multihash, null));
        records.invalidate(multihash);
    }

    private void flush() {
        dhtRecordDao.batchSave(persistTasks);
    }

    @Override
    public void close() throws Exception {
        scheduled.shutdownNow();
        records.asMap().forEach((hash, record) -> persistTasks.offer(new DHTRecordDao.PersistTask(false, hash, record)));
        records.invalidateAll();
        flush();
    }
}
