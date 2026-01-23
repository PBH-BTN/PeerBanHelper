package com.ghostchu.peerbanhelper.module.impl.monitor;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.databasent.service.PeerConnectionMetricsService;
import com.ghostchu.peerbanhelper.databasent.service.PeerConnectionMetricsTrackService;
import com.ghostchu.peerbanhelper.databasent.table.PeerConnectionMetricsEntity;
import com.ghostchu.peerbanhelper.databasent.table.PeerConnectionMetricsTrackEntity;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.MonitorFeatureModule;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SessionAnalyseServiceModule extends AbstractFeatureModule implements Reloadable, MonitorFeatureModule {
    @Autowired
    private PeerConnectionMetricsTrackService connectionMetricsTrackDao;
    @Autowired
    private PeerConnectionMetricsService connectionMetricDao;
    private long cleanupInterval;
    private long dataRetentionTime;
    private long dataFlushInterval;

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void onTorrentPeersRetrieved(@NotNull Downloader downloader, @NotNull Torrent torrent, @NotNull List<Peer> peers) {
        try {
            connectionMetricsTrackDao.syncPeers(downloader, torrent, peers);
        } catch (SQLException | ExecutionException e) {
            log.warn("Failed to record torrent peers for session analyse", e);
            Sentry.captureException(e);
        }
    }

    @Override
    public @NotNull String getName() {
        return "Session Analyse Service";
    }

    @Override
    public @NotNull String getConfigName() {
        return "peer-analyse-service.session-analyse";
    }

    @Override
    public void onEnable() {
        reloadConfig();
        this.cleanupInterval = getConfig().getLong("cleanup-interval");
        this.dataFlushInterval = getConfig().getLong("data-flush-interval");
        registerScheduledTask(this::cleanup, 0, this.cleanupInterval, TimeUnit.MILLISECONDS);
        registerScheduledTask(this::flushData, 0, this.dataFlushInterval, TimeUnit.MILLISECONDS);
        Main.getReloadManager().register(this);
    }

    private void flushData() {
        try {
            connectionMetricsTrackDao.flushAll();
            long startOfToday = MiscUtil.getStartOfToday(System.currentTimeMillis());
            List<PeerConnectionMetricsTrackEntity> listNotInTheDay = connectionMetricsTrackDao.queryBuilder().where().ne("timeframeAt", new Timestamp(startOfToday)).query();
            List<PeerConnectionMetricsEntity> aggNotInTheDayList = connectionMetricDao.aggregating(listNotInTheDay);
            connectionMetricDao.saveAggregating(aggNotInTheDayList, true);
            connectionMetricsTrackDao.deleteEntries(listNotInTheDay); // do not use batchDelete: workaround for [BUG] [SQLITE_TOOBIG] String or BLOB exceeds size limit (statement too long) #1518
            List<PeerConnectionMetricsTrackEntity> listInTheDay = connectionMetricsTrackDao.queryBuilder().where().eq("timeframeAt", new Timestamp(startOfToday)).query();
            List<PeerConnectionMetricsEntity> aggInTheDayList = connectionMetricDao.aggregating(listInTheDay);
            connectionMetricDao.saveAggregating(aggInTheDayList, true);
        } catch (SQLException e) {
            log.warn("Failed to flush session analyse data", e);
            Sentry.captureException(e);
        }
    }

    private void cleanup() {
        connectionMetricDao.removeOutdatedData(OffsetDateTime.now().minus(this.dataRetentionTime, ChronoUnit.MILLIS));
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }

    private void reloadConfig() {
        this.dataRetentionTime = getConfig().getLong("data-retention-time");
    }

    @Override
    public void onDisable() {
        flushData();
    }


}
