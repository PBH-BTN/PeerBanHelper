package com.ghostchu.peerbanhelper.metric.impl.persist;

import com.ghostchu.peerbanhelper.database.BanLog;
import com.ghostchu.peerbanhelper.database.DatabaseHelper;
import com.ghostchu.peerbanhelper.metric.Metrics;
import com.ghostchu.peerbanhelper.metric.impl.inmemory.InMemoryMetrics;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;

@Slf4j
public class PersistMetrics implements Metrics {
    private final DatabaseHelper db;
    private final Metrics inMemory = new InMemoryMetrics();
    private final Deque<Map.Entry<PeerAddress, BanMetadata>> memoryBuffer = new LinkedBlockingDeque<>();

    public PersistMetrics(DatabaseHelper db) {
        this.db = db;
    }

    @Override
    public long getCheckCounter() {
        return inMemory.getCheckCounter();
    }

    @Override
    public long getPeerBanCounter() {
        return inMemory.getPeerBanCounter();
    }

    @Override
    public long getPeerUnbanCounter() {
        return inMemory.getPeerUnbanCounter();
    }

    @Override
    public void recordCheck() {
        inMemory.recordCheck();
    }

    @Override
    public void recordPeerBan(PeerAddress address, BanMetadata metadata) {
        inMemory.recordPeerBan(address, metadata);
        memoryBuffer.offer(Map.entry(address, metadata));
    }

    @Override
    public void recordPeerUnban(PeerAddress address, BanMetadata metadata) {
        inMemory.recordPeerUnban(address, metadata);
        // no record
    }

    @Override
    public void flush() {
        long startAt = System.currentTimeMillis();
        try {
            List<BanLog> logs = new LinkedList<>();
            while (!memoryBuffer.isEmpty()) {
                Map.Entry<PeerAddress, BanMetadata> e = memoryBuffer.poll();
                logs.add(new BanLog(
                        e.getValue().getBanAt(),
                        e.getValue().getUnbanAt(),
                        e.getKey().getIp(),
                        e.getKey().getPort(),
                        e.getValue().getPeer().getId(),
                        e.getValue().getPeer().getClientName(),
                        e.getValue().getPeer().getUploaded(),
                        e.getValue().getPeer().getDownloaded(),
                        e.getValue().getPeer().getProgress(),
                        e.getValue().getTorrent().getHash(),
                        e.getValue().getTorrent().getHash(),
                        e.getValue().getTorrent().getSize(),
                        e.getValue().getContext(),
                        e.getValue().getDescription()
                ));
            }
            long lines = db.insertBanLogs(logs);
            log.info(Lang.DATABASE_BUFFER_SAVED, lines, System.currentTimeMillis() - startAt);
        } catch (SQLException e) {
            log.warn(Lang.DATABASE_SAVE_BUFFER_FAILED, System.currentTimeMillis() - startAt, e);
        }
    }

    @Override
    public void close() {
        inMemory.close();
        flush();
    }
}
