package com.ghostchu.peerbanhelper.metric.impl.persist;

import com.ghostchu.peerbanhelper.database.BanLog;
import com.ghostchu.peerbanhelper.database.DatabaseHelper;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.metric.impl.inmemory.InMemoryMetrics;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Slf4j
@Component("persistMetrics")
public class PersistMetrics implements BasicMetrics {
    @Autowired
    private DatabaseHelper db;
    @Autowired
    private InMemoryMetrics inMemory;

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
        try {
            db.insertBanLogs(new BanLog(
                    metadata.getBanAt(),
                    metadata.getUnbanAt(),
                    address.getIp(),
                    address.getPort(),
                    metadata.getPeer().getId(),
                    metadata.getPeer().getClientName(),
                    metadata.getPeer().getUploaded(),
                    metadata.getPeer().getDownloaded(),
                    metadata.getPeer().getProgress(),
                    metadata.getTorrent().getHash(),
                    metadata.getTorrent().getName(),
                    metadata.getTorrent().getSize(),
                    metadata.getContext(),
                    metadata.getDescription()
            ));
        } catch (SQLException e) {
            log.warn(Lang.DATABASE_SAVE_BUFFER_FAILED, e);
        }
    }

    @Override
    public void recordPeerUnban(PeerAddress address, BanMetadata metadata) {
        inMemory.recordPeerUnban(address, metadata);
        // no record
    }

    @Override
    public void flush() {
//        long startAt = System.currentTimeMillis();
//        try {
//            List<BanLog> logs = new LinkedList<>();
//            while (!memoryBuffer.isEmpty()) {
//                Map.Entry<PeerAddress, BanMetadata> e = memoryBuffer.poll();
//                logs.add();
//            }
//            long lines = db.insertBanLogs(logs);
//            log.info(Lang.DATABASE_BUFFER_SAVED, lines, System.currentTimeMillis() - startAt);
//        } catch (SQLException e) {
//
//        }
    }

    @Override
    public void close() {
        inMemory.close();
        flush();
    }
}
