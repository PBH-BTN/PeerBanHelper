package com.ghostchu.peerbanhelper.metric.impl.persist;

import com.ghostchu.peerbanhelper.database.DatabaseHelper;
import com.ghostchu.peerbanhelper.database.dao.ModuleDao;
import com.ghostchu.peerbanhelper.database.dao.PeerIdentityDao;
import com.ghostchu.peerbanhelper.database.dao.RuleDao;
import com.ghostchu.peerbanhelper.database.dao.TorrentDao;
import com.ghostchu.peerbanhelper.database.table.*;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.metric.impl.inmemory.InMemoryMetrics;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;

@Slf4j
@Component("persistMetrics")
public class PersistMetrics implements BasicMetrics {
    @Autowired
    private DatabaseHelper db;
    @Autowired
    private InMemoryMetrics inMemory;
    @Autowired
    private TorrentDao dao;
    @Autowired
    private PeerIdentityDao peerIdentityDao;
    @Autowired
    private TorrentDao torrentDao;
    @Autowired
    private ModuleDao moduleDao;
    @Autowired
    private RuleDao ruleDao;

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
            PeerIdentityEntity peerIdentityEntity = peerIdentityDao.createIfNotExists(new PeerIdentityEntity(
                    null,
                    metadata.getPeer().getId(),
                    metadata.getPeer().getClientName()
            ));
            TorrentEntity torrentEntity = torrentDao.createIfNotExists(new TorrentEntity(
                    metadata.getTorrent().getHash(),
                    metadata.getTorrent().getName(),
                    metadata.getTorrent().getSize()
            ));
            ModuleEntity module = moduleDao.createIfNotExists(new ModuleEntity(
                    null,
                    metadata.getContext()
            ));
            RuleEntity rule = ruleDao.createIfNotExists(new RuleEntity(
                    null,
                    module,
                    metadata.getRule()
            ));
            Dao<HistoryEntity, Long> historyDao = DaoManager.createDao(db.getDataSource(), HistoryEntity.class);
            historyDao.create(new HistoryEntity(
                    null,
                    new Timestamp(metadata.getBanAt()),
                    new Timestamp(metadata.getUnbanAt()),
                    address.getIp(),
                    address.getPort(),
                    peerIdentityEntity,
                    metadata.getPeer().getUploaded(),
                    metadata.getPeer().getDownloaded(),
                    metadata.getPeer().getProgress(),
                    torrentEntity,
                    rule,
                    metadata.getDescription(),
                    metadata.getPeer().getFlags()
            ));
        } catch (SQLException e) {
            log.error(Lang.DATABASE_SAVE_BUFFER_FAILED, e);
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
