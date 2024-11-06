package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.ghostchu.peerbanhelper.database.dao.impl.TrafficJournalDao;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.event.LivePeersUpdatedEvent;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.eventbus.Subscribe;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
@IgnoreScan
public class ActiveMonitoringModule extends AbstractFeatureModule implements Reloadable {
    private final PeerRecordDao peerRecordDao;
    private final Deque<PeerRecordDao.BatchHandleTasks> dataBuffer = new ConcurrentLinkedDeque<>();
    private final TrafficJournalDao trafficJournalDao;
    private final BlockingDeque<Runnable> taskWriteQueue = new LinkedBlockingDeque<>();
    private final Cache<PeerRecordDao.BatchHandleTasks, Object> diskWriteCache = CacheBuilder
            .newBuilder()
            .expireAfterWrite(3, TimeUnit.MINUTES)
            .maximumSize(3500)
            .removalListener(notification -> dataBuffer.offer((PeerRecordDao.BatchHandleTasks) notification.getKey()))
            .build();
    private final AlertManager alertManager;
    private long dailyTrafficCapping;
    private ExecutorService taskWriteService;
    private long dataRetentionTime;

    public ActiveMonitoringModule(PeerRecordDao peerRecordDao, TrafficJournalDao trafficJournalDao, AlertManager alertManager) {
        super();
        this.peerRecordDao = peerRecordDao;
        this.trafficJournalDao = trafficJournalDao;
        this.alertManager = alertManager;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public @NotNull String getName() {
        return "Active Monitoring";
    }

    @Override
    public @NotNull String getConfigName() {
        return "active-monitoring";
    }

    @Subscribe
    private void onLivePeerSnapshotEvent(LivePeersUpdatedEvent event) {
        event.getLivePeers().values().stream().flatMap(Collection::stream)
                .filter(peerMetadata -> {
                    var clientName = peerMetadata.getPeer().getClientName();
                    var peerId = peerMetadata.getPeer().getId();
                    if (clientName != null && !clientName.isBlank()) {
                        return true;
                    }
                    if (peerId != null && !peerId.isBlank()) {
                        return true;
                    }
                    if (peerMetadata.getPeer().getProgress() > 0) {
                        return true;
                    }
                    if (peerMetadata.getPeer().getDownloadSpeed() > 0) {
                        return true;
                    }
                    return peerMetadata.getPeer().getUploadSpeed() > 0;
                })
                .forEach(meta -> diskWriteCache.put(new PeerRecordDao.BatchHandleTasks(System.currentTimeMillis(), meta.getDownloader(), meta.getTorrent(), meta.getPeer()), MiscUtil.EMPTY_OBJECT));
    }

    @Override
    public void onEnable() {
        reloadConfig();
        this.taskWriteService = new ThreadPoolExecutor(1, 2, 60L, TimeUnit.SECONDS, taskWriteQueue);
        Main.getEventBus().register(this);
        Main.getReloadManager().register(this);
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }

    private void reloadConfig() {
        if (this.taskWriteService != null) {
            this.taskWriteService.shutdown();
        }
        this.taskWriteService = Executors.newVirtualThreadPerTaskExecutor();
        this.dataRetentionTime = getConfig().getLong("data-retention-time", -1);
        long dataCleanupInterval = getConfig().getLong("data-cleanup-interval", -1);
        this.dailyTrafficCapping = getConfig().getLong("traffic-monitoring.daily", -1);
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::cleanup, 0, dataCleanupInterval, TimeUnit.MILLISECONDS);
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::flush, 20, 20, TimeUnit.SECONDS);
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::writeJournal, 0, 1, TimeUnit.HOURS);
    }

    private void writeJournal() {
        for (Downloader downloader : getServer().getDownloaders()) {
            try {
                var entity = trafficJournalDao.getTodayJournal(downloader.getName());
                if (downloader.login().success()) {
                    var stats = downloader.getStatistics();
                    entity.setDataOverallUploaded(stats.totalUploaded());
                    entity.setDataOverallDownloaded(stats.totalDownloaded());
                    trafficJournalDao.update(entity);
                }
            } catch (Throwable e) {
                log.error("Unable to write hourly traffic journal to database", e);
            }
        }
        updateTrafficMonitoringService();
    }

    @SneakyThrows
    private void updateTrafficMonitoringService() {
        if (dailyTrafficCapping <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        // Calculating the today traffic
        long startOfToday = MiscUtil.getStartOfToday(now);
        var data = trafficJournalDao.getDayOffsetData(null, new Timestamp(startOfToday - 86400000), new Timestamp(now), trafficJournalDao::fixTimezone);
        long totalBytes = 0;
        for (TrafficJournalDao.TrafficData datum : data) {
            totalBytes += datum.getDataOverallUploaded();
        }
        var dateTimeString = MiscUtil.formatDateTime(now);
        var dateString = MiscUtil.formatDateOnly(now);
        var identifier = "dataTrafficCapping-" + startOfToday;
        // 一天只发一次
        if (totalBytes >= dailyTrafficCapping && !alertManager.identifierAlertExistsIncludeRead(identifier)) {
            alertManager.publishAlert(true,
                    AlertLevel.WARN,
                    identifier,
                    new TranslationComponent(Lang.MODULE_AMM_TRAFFIC_MONITORING_TRAFFIC_ALERT_TITLE, dateString),
                    new TranslationComponent(Lang.MODULE_AMM_TRAFFIC_MONITORING_TRAFFIC_ALERT_DESCRIPTION,
                            dateTimeString,
                            FileUtils.byteCountToDisplaySize(totalBytes),
                            FileUtils.byteCountToDisplaySize(dailyTrafficCapping)));
        }
    }

    public void flush() {
        try {
            try {
                peerRecordDao.syncPendingTasks(dataBuffer);
            } catch (SQLException e) {
                log.warn("Unable sync peers data to database", e);
            }
        } catch (Throwable throwable) {
            log.error("Unable to complete scheduled tasks", throwable);
        }
    }

    private void cleanup() {
        try {
            if (dataRetentionTime <= 0) {
                return;
            }
            log.info(tlUI(Lang.AMM_CLEANING_TABLES));
            try {
                var deleteBuilder = peerRecordDao.deleteBuilder();
                var where = deleteBuilder.where()
                        .lt("lastTimeSeen", dataRetentionTime);
                deleteBuilder.setWhere(where);
                int deleted = deleteBuilder.delete();
                log.info(tlUI(Lang.AMM_CLEANED_UP, deleted));
            } catch (SQLException e) {
                log.warn("Unable to clean up AMM tables", e);
            }
        } catch (Throwable throwable) {
            log.error("Unable to complete scheduled tasks", throwable);
        }
    }

    @Override
    public void onDisable() {
        Main.getEventBus().unregister(this);
        diskWriteCache.invalidateAll();
        writeJournal();
        flush();
        taskWriteService.shutdown();
        try {
            log.info(tlUI(Lang.AMM_SHUTTING_DOWN));
            if (!taskWriteService.awaitTermination(10, TimeUnit.SECONDS)) {
                taskWriteService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Main.getReloadManager().unregister(this);
    }

}
