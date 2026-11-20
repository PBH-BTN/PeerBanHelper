package com.ghostchu.peerbanhelper.module.impl.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.databasent.service.PeerConnectionMetricsService;
import com.ghostchu.peerbanhelper.databasent.service.PeerConnectionMetricsTrackService;
import com.ghostchu.peerbanhelper.databasent.table.PeerConnectionMetricsTrackEntity;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SessionAnalyseServiceModule extends AbstractFeatureModule implements Reloadable, BatchMonitorFeatureModule {
    @Autowired
    private PeerConnectionMetricsTrackService connectionMetricsTrackDao;
    @Autowired
    private PeerConnectionMetricsService connectionMetricDao;
    @Autowired
    private TransactionTemplate transactionTemplate;
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
        } catch (ExecutionException e) {
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

    private synchronized void flushData() {
        connectionMetricsTrackDao.flushAll();
        var last = connectionMetricsTrackDao.getOne(new LambdaQueryWrapper<PeerConnectionMetricsTrackEntity>()
                .select(PeerConnectionMetricsTrackEntity::getId).orderByDesc(PeerConnectionMetricsTrackEntity::getId).last("LIMIT 1"));
        if (last == null) return;

        long after = 0;
        while (after < last.getId()) {
            final long cursor = after;
            Long next = transactionTemplate.execute(_ -> {
                var page = connectionMetricsTrackDao.list(new LambdaQueryWrapper<PeerConnectionMetricsTrackEntity>()
                        .select(PeerConnectionMetricsTrackEntity::getId, PeerConnectionMetricsTrackEntity::getTimeframeAt,
                                PeerConnectionMetricsTrackEntity::getDownloader, PeerConnectionMetricsTrackEntity::getLastFlags)
                        .gt(PeerConnectionMetricsTrackEntity::getId, cursor).le(PeerConnectionMetricsTrackEntity::getId, last.getId())
                        .orderByAsc(PeerConnectionMetricsTrackEntity::getId).last("LIMIT 1024"));
                if (page.isEmpty()) return null;
                long end = page.getLast().getId();
                int deleted = connectionMetricsTrackDao.getBaseMapper().delete(new LambdaQueryWrapper<PeerConnectionMetricsTrackEntity>()
                        .gt(PeerConnectionMetricsTrackEntity::getId, cursor).le(PeerConnectionMetricsTrackEntity::getId, end));
                if (deleted != page.size()) {
                    throw new IllegalStateException("Session observations changed during aggregation");
                }
                // Each consumed observation contributes once, including a previous day's final page.
                connectionMetricDao.saveAggregating(connectionMetricDao.aggregating(page), false);
                return end;
            });
            if (next == null) break;
            after = next;
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
        try {
            connectionMetricsTrackDao.closeCache();
        } catch (Exception e) {
            log.warn("Unable to close session analyse cache instance", e);
        }
    }


}
