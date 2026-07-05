package com.ghostchu.peerbanhelper.metric.impl.persist;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.databasent.service.HistoryService;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;
import com.ghostchu.peerbanhelper.downloader.DownloaderBasicInfo;
import com.ghostchu.peerbanhelper.event.program.PBHServerStartedEvent;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.metric.impl.inmemory.InMemoryMetrics;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.ipdb.IPDBManager;
import com.ghostchu.peerbanhelper.util.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.wrapper.*;
import com.google.common.eventbus.Subscribe;
import inet.ipaddr.IPAddress;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component("persistMetrics")
public final class PersistMetrics extends InMemoryMetrics implements BasicMetrics {
    private final TorrentService torrentDao;
    private final HistoryService historyDao;
    private final IPDBManager ipdbManager;

    public PersistMetrics(HistoryService historyDao, TorrentService torrentDao, IPDBManager ipdbManager) {
        this.historyDao = historyDao;
        this.torrentDao = torrentDao;
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
    public BanMetadata recordPeerBan(@NotNull IPAddress address, DownloaderBasicInfo downloader, OffsetDateTime banAt, OffsetDateTime unbanAt,
                                     boolean excludeFromPersist, boolean excludeFromNotify, boolean excludeFromReport, boolean excludeFromDisplay,
                                     TorrentWrapper torrent, PeerWrapper peer, BanDetailData banDetailData) {
        BanMetadata metadata = super.recordPeerBan(address, downloader, banAt, unbanAt, excludeFromPersist, excludeFromNotify,
                excludeFromReport, excludeFromDisplay, torrent, peer,
                banDetailData);
        if (metadata.isExcludeFromPersist()) {
            return metadata;
        }
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
        var entity = new HistoryEntity(
                null,
                banAt,
                unbanAt,
                address.toInetAddress(),
                peer.getAddress().getPort(),
                peer.getId(),
                peer.getClientName(),
                peer.getUploaded(),
                peer.getDownloaded(),
                peer.getProgress(),
                torrent.getProgress(),
                torrentEntity.getId(),
                banDetailData.context(),
                banDetailData.rule(),
                banDetailData.description(),
                metadata.getPeer().getFlags() == null ? null : metadata.getPeer().getFlags(),
                metadata.getDownloader().id(),
                banDetailData.structuredData(),
                geoIpData
        );
        historyDao.save(entity);
        metadata.setLinkedHistoryId(entity.getId());
        return metadata;
    }


    @Override
    public void recordPeerUnban(@NotNull IPAddress address, @NotNull BanMetadata metadata) {
        super.recordPeerUnban(address, metadata);
        if (metadata.isExcludeFromNotify()) {
            return;
        }
        // no record
    }

    @Override
    public void flush() {

    }

    @Override
    public void close() {
        super.close();
        flush();
    }
}
