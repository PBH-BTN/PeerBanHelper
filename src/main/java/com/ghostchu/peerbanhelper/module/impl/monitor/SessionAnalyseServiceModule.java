package com.ghostchu.peerbanhelper.module.impl.monitor;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerConnectionMetricDao;
import com.ghostchu.peerbanhelper.database.dao.impl.TorrentDao;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerConnectionMetricsTrackDao;
import com.ghostchu.peerbanhelper.database.table.TorrentEntity;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.MonitorFeatureModule;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SessionAnalyseServiceModule extends AbstractFeatureModule implements Reloadable, MonitorFeatureModule {
    @Autowired
    private PeerConnectionMetricsTrackDao connectionMetricsTrackDao;
    @Autowired
    private PeerConnectionMetricDao connectionMetricDao;
    private long cleanupInterval;
    private long dataRetentionTime;
    private long dataFlushInterval;
    @Autowired
    private JavalinWebContainer javalinWebContainer;
    @Autowired
    private TorrentDao torrentDao;

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void onTorrentPeersRetrieved(@NotNull Downloader downloader, @NotNull Torrent torrent, @NotNull List<Peer> peers) {
        try {
            TorrentEntity torrentEntity = torrentDao.createIfNotExists(new TorrentEntity(
                    null,
                    torrent.getHash(),
                    torrent.getName(),
                    torrent.getSize(),
                    torrent.isPrivate()
            ));
            connectionMetricsTrackDao.upsertPeerSession(downloader, torrentEntity, peers);
        } catch (SQLException e) {
            log.warn("Failed to record torrent peers for session analyse", e);
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
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::cleanup, 0, this.cleanupInterval, TimeUnit.MILLISECONDS);
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::flushData, 0, this.dataFlushInterval, TimeUnit.MILLISECONDS);
        Main.getReloadManager().register(this);
    }

    private void flushData() {
        try {
            long startOfToday = MiscUtil.getStartOfToday(System.currentTimeMillis());
            var listNotInTheDay = connectionMetricsTrackDao.queryBuilder().where().ne("timeframeAt", new Timestamp(startOfToday)).query();
            var aggNotInTheDayList = connectionMetricDao.aggregating(listNotInTheDay);
            connectionMetricDao.saveAggregating(aggNotInTheDayList, true);
            connectionMetricsTrackDao.delete(listNotInTheDay);
            var listInTheDay = connectionMetricsTrackDao.queryBuilder().where().eq("timeframeAt", new Timestamp(startOfToday)).query();
            var aggInTheDayList = connectionMetricDao.aggregating(listInTheDay);
            connectionMetricDao.saveAggregating(aggInTheDayList, true);
        } catch (SQLException e) {
            log.warn("Failed to flush session analyse data", e);
        }
    }

    private void cleanup() {
        connectionMetricDao.removeOutdatedData(new Timestamp(System.currentTimeMillis() - this.dataRetentionTime));
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
