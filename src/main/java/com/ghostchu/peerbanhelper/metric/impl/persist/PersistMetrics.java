package com.ghostchu.peerbanhelper.metric.impl.persist;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.databasent.service.HistoryService;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;
import com.ghostchu.peerbanhelper.event.program.PBHServerStartedEvent;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.metric.impl.inmemory.InMemoryMetrics;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.ipdb.IPDBManager;
import com.ghostchu.peerbanhelper.util.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.google.common.eventbus.Subscribe;
import inet.ipaddr.IPAddress;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component("persistMetrics")
public final class PersistMetrics implements BasicMetrics {
    private final InMemoryMetrics inMemory;
    private final TorrentService torrentDao;
    private final HistoryService historyDao;
    private final IPDBManager ipdbManager;


    public PersistMetrics(HistoryService historyDao, TorrentService torrentDao, InMemoryMetrics inMemory, IPDBManager ipdbManager) {
        this.historyDao = historyDao;
        this.torrentDao = torrentDao;
        this.inMemory = inMemory;
        this.ipdbManager = ipdbManager;
        Main.getEventBus().register(this);
    }

    @Subscribe
    public void init(PBHServerStartedEvent event) {
        CommonUtil.getBgCleanupScheduler().scheduleWithFixedDelay(this::cleanup, 0, 1, TimeUnit.DAYS);
    }

    private void cleanup() {
        try {
            int keepDays = Main.getMainConfig().getInt("persist.ban-logs-keep-days");
            if (keepDays > 0) {
                try {
                    long deletes = historyDao.deleteExpiredLogs(keepDays);
                    log.info(tlUI(Lang.CLEANED_BANLOGS, deletes));
                } catch (Exception e) {
                    log.error("Unable to cleanup expired banlogs", e);
                    Sentry.captureException(e);
                }
            }
        } catch (Throwable throwable) {
            log.error("Unable to complete scheduled tasks", throwable);
            Sentry.captureException(throwable);
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
    public long getSavedTraffic() {
        return inMemory.getSavedTraffic();
    }

    @Override
    public long getWastedTraffic() {
        return inMemory.getWastedTraffic();
    }

    @Override
    public void recordCheck() {
        inMemory.recordCheck();
    }

    @Override
    public void recordPeerBan(@NotNull IPAddress address, @NotNull BanMetadata metadata) {
        if (metadata.isBanForDisconnect()) {
            return;
        }
        inMemory.recordPeerBan(address, metadata);
        TorrentEntity torrentEntity = torrentDao.createIfNotExists(new TorrentEntity(
                null,
                metadata.getTorrent().getHash(),
                metadata.getTorrent().getName(),
                metadata.getTorrent().getSize(),
                metadata.getTorrent().isPrivateTorrent()
        ));
        IPGeoData geoIpData = null;
        var resp = ipdbManager.queryIPDB(metadata.getPeer().getAddress().getAddress().toInetAddress());
        if (resp != null) {
            geoIpData = resp.geoData().get();
        }
        historyDao.save(new HistoryEntity(
                null,
                metadata.getBanAt(),
                metadata.getUnbanAt(),
                address.toInetAddress(),
                metadata.getPeer().getAddress().getPort(),
                metadata.getPeer().getId(),
                metadata.getPeer().getClientName(),
                metadata.getPeer().getUploaded(),
                metadata.getPeer().getDownloaded(),
                metadata.getPeer().getProgress(),
                metadata.getTorrent().getProgress(),
                torrentEntity.getId(),
                metadata.getContext(),
                metadata.getRule(),
                metadata.getDescription(),
                metadata.getPeer().getFlags() == null ? null : metadata.getPeer().getFlags(),
                metadata.getDownloader().id(),
                metadata.getStructuredData() == null ? null : metadata.getStructuredData(),
                geoIpData
        ));
    }

    @Override
    public void recordPeerUnban(@NotNull IPAddress address, @NotNull BanMetadata metadata) {
        if (metadata.isBanForDisconnect()) {
            return;
        }
        inMemory.recordPeerUnban(address, metadata);
        // no record
    }

    @Override
    public void flush() {

    }

    @Override
    public void close() {
        inMemory.close();
        flush();
    }
}
