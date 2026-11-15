package com.ghostchu.peerbanhelper.module.impl.monitor;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.databasent.service.PeerConnectionMetricsService;
import com.ghostchu.peerbanhelper.databasent.service.PeerConnectionMetricsTrackService;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BatchMonitorFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTaskManager;
import com.ghostchu.peerbanhelper.util.backgroundtask.FunctionalBackgroundTask;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SessionAnalyseServiceModule extends AbstractFeatureModule implements Reloadable, BatchMonitorFeatureModule {
    @Autowired
    private PeerConnectionMetricsTrackService connectionMetricsTrackDao;
    @Autowired
    private PeerConnectionMetricsService connectionMetricDao;
    private long cleanupInterval;
    private long dataRetentionTime;
    private long dataFlushInterval;
    @Autowired
    private BackgroundTaskManager backgroundTaskManager;

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void onPeersRetrieved(@NotNull Downloader downloader, Torrent torrent, List<? extends Peer> peers, @NotNull PipelineTask<?> task) {
        try {
            task.setComment(true, "Sync Peers data with DB, flush to disk if needed.");
            connectionMetricsTrackDao.syncPeers(downloader, torrent, peers);
        } catch (RuntimeException e) {
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
        CommonUtil.getBgCleanupScheduler().scheduleWithFixedDelay(this::cleanup, 0, this.cleanupInterval, TimeUnit.MILLISECONDS);
        registerScheduledTask(this::flushData, 0, this.dataFlushInterval, TimeUnit.MILLISECONDS);
        Main.getReloadManager().register(this);
    }

    private void flushData() {
        try {
            connectionMetricsTrackDao.aggregatePending();
        } catch (RuntimeException e) {
            log.warn("Unable to aggregate session metrics", e);
            Sentry.captureException(e);
        }
    }

    private void cleanup() {
        backgroundTaskManager.addTaskAsync(new FunctionalBackgroundTask(
                new TranslationComponent(Lang.MODULE_PEER_ANALYSING_DELETING_EXPIRED_DATA),
                (_, _) -> connectionMetricDao.removeOutdatedData(OffsetDateTime.now().minus(this.dataRetentionTime, ChronoUnit.MILLIS))
        )).join();
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
