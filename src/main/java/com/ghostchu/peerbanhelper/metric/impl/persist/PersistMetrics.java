package com.ghostchu.peerbanhelper.metric.impl.persist;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.database.dao.impl.ModuleDao;
import com.ghostchu.peerbanhelper.database.dao.impl.RuleDao;
import com.ghostchu.peerbanhelper.database.dao.impl.TorrentDao;
import com.ghostchu.peerbanhelper.database.table.HistoryEntity;
import com.ghostchu.peerbanhelper.database.table.ModuleEntity;
import com.ghostchu.peerbanhelper.database.table.RuleEntity;
import com.ghostchu.peerbanhelper.database.table.TorrentEntity;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.metric.impl.inmemory.InMemoryMetrics;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component("persistMetrics")
public class PersistMetrics implements BasicMetrics {
    private final InMemoryMetrics inMemory;
    private final TorrentDao torrentDao;
    private final ModuleDao moduleDao;
    private final RuleDao ruleDao;
    private final HistoryDao historyDao;

    public PersistMetrics(HistoryDao historyDao, RuleDao ruleDao, ModuleDao moduleDao, TorrentDao torrentDao, InMemoryMetrics inMemory) {
        this.historyDao = historyDao;
        this.ruleDao = ruleDao;
        this.moduleDao = moduleDao;
        this.torrentDao = torrentDao;
        this.inMemory = inMemory;
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::cleanup, 0, 1, TimeUnit.DAYS);
    }

    private void cleanup() {
        try {
            int keepDays = Main.getMainConfig().getInt("persist.ban-logs-keep-days");
            if (keepDays > 0) {
                try {
                    var builder = historyDao.deleteBuilder();
                    builder.setWhere(builder
                            .where()
                            .le("banAt",
                                    new Timestamp(LocalDateTime.now().minusDays(keepDays)
                                            .toInstant(MiscUtil.getSystemZoneOffset())
                                            .toEpochMilli())));
                    log.info(tlUI(Lang.CLEANED_BANLOGS, builder.delete()));
                } catch (Exception e) {
                    log.error("Unable to cleanup expired banlogs", e);
                }
            }
        } catch (Throwable throwable) {
            log.error("Unable to complete scheduled tasks", throwable);
        }
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

    /**
     * Records a peer ban in the metrics system and persists ban details to the database.
     *
     * This method records a peer ban in the in-memory metrics and asynchronously saves
     * the ban details to the database using a virtual thread. It skips recording if the
     * ban is due to a disconnection.
     *
     * @param address The network address of the peer being banned
     * @param metadata Metadata containing details about the ban, including torrent, peer,
     *                 and rule information
     *
     * @throws SQLException If there is an error saving the ban details to the database
     *
     * @implNote Uses virtual threads to offload database I/O operations and prevent
     *           blocking the main thread during database interactions
     */
    @Override
    public void recordPeerBan(PeerAddress address, BanMetadata metadata) {
        if(metadata.isBanForDisconnect()){
            return;
        }
        inMemory.recordPeerBan(address, metadata);
        // 将数据库 IO 移动到虚拟线程上
        Thread.ofVirtual().start(() -> {
            try {
                TorrentEntity torrentEntity = torrentDao.createIfNotExists(new TorrentEntity(
                        null,
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
                historyDao.create(new HistoryEntity(
                        null,
                        new Timestamp(metadata.getBanAt()),
                        new Timestamp(metadata.getUnbanAt()),
                        address.getAddress().toNormalizedString(),
                        address.getPort(),
                        metadata.getPeer().getId(),
                        metadata.getPeer().getClientName(),
                        metadata.getPeer().getUploaded(),
                        metadata.getPeer().getDownloaded(),
                        metadata.getPeer().getProgress(),
                        torrentEntity,
                        rule,
                        metadata.getDescription(),
                        metadata.getPeer().getFlags() == null ? null : metadata.getPeer().getFlags().toString(),
                        metadata.getDownloader()
                ));
            } catch (SQLException e) {
                log.error(tlUI(Lang.DATABASE_SAVE_BUFFER_FAILED), e);
            }
        });
    }

    @Override
    public void recordPeerUnban(PeerAddress address, BanMetadata metadata) {
        if(metadata.isBanForDisconnect()){
            return;
        }
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
