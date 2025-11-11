package com.ghostchu.peerbanhelper.module.impl.monitor;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.ghostchu.peerbanhelper.database.dao.impl.TorrentDao;
import com.ghostchu.peerbanhelper.database.dao.impl.TrafficJournalDao;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderFeatureFlag;
import com.ghostchu.peerbanhelper.downloader.DownloaderManagerImpl;
import com.ghostchu.peerbanhelper.downloader.DownloaderSpeedLimiter;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.MonitorFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.ghostchu.peerbanhelper.wrapper.PeerWrapper;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public final class ActiveMonitoringModule extends AbstractFeatureModule implements Reloadable, MonitorFeatureModule {
    @Autowired
    private PeerRecordDao peerRecordDao;
    private final Deque<PeerRecordDao.BatchHandleTasks> dataBuffer = new ConcurrentLinkedDeque<>();
    @Autowired
    private TrafficJournalDao trafficJournalDao;
    private final BlockingDeque<Runnable> taskWriteQueue = new LinkedBlockingDeque<>();
    private final Cache<PeerRecordDao.BatchHandleTasks, Object> diskWriteCache = CacheBuilder
            .newBuilder()
            .expireAfterWrite(ExternalSwitch.parseLong("pbh.module.activeMonitorModule.diskWriteCache.timeout", 180000), TimeUnit.MILLISECONDS)
            .maximumSize(ExternalSwitch.parseInt("pbh.module.activeMonitorModule.diskWriteCache.size", 3500))
            .removalListener(notification -> dataBuffer.offer((PeerRecordDao.BatchHandleTasks) notification.getKey()))
            .build();
    @Autowired
    private AlertManager alertManager;
    private long dailyTrafficCapping;
    private ExecutorService taskWriteService;
    private long dataRetentionTime;
    @Autowired
    private DownloaderManagerImpl downloaderManager;
    @Autowired
    private TorrentDao torrentDao;
    private boolean useTrafficSlidingCapping;
    private long maxTrafficAllowedInWindowPeriod;
    private long trafficSlidingCappingMaxSpeed;
    private long trafficSlidingCappingMinSpeed;

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

    @Override
    public void onEnable() {
        reloadConfig();
        this.taskWriteService = new ThreadPoolExecutor(1, 2, 60L, TimeUnit.SECONDS, taskWriteQueue);
        long dataCleanupInterval = getConfig().getLong("data-cleanup-interval", -1);
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::cleanup, 0, dataCleanupInterval, TimeUnit.MILLISECONDS);
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::flush, 20, 20, TimeUnit.SECONDS);
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::updateTrafficStatus, 0, 1, TimeUnit.MINUTES);
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
        this.taskWriteService = Executors.newSingleThreadExecutor();
        this.dataRetentionTime = getConfig().getLong("data-retention-time", -1);
        this.dailyTrafficCapping = getConfig().getLong("traffic-monitoring.daily", -1);
        this.useTrafficSlidingCapping = getConfig().getBoolean("traffic-sliding-capping.enabled");
        this.maxTrafficAllowedInWindowPeriod = getConfig().getLong("traffic-sliding-capping.daily-max-allowed-upload-traffic");
        this.trafficSlidingCappingMaxSpeed = getConfig().getLong("traffic-sliding-capping.max-speed");
        this.trafficSlidingCappingMinSpeed = getConfig().getLong("traffic-sliding-capping.min-speed");
    }

    private void updateTrafficStatus() {
        for (Downloader downloader : downloaderManager) {
            try {
                if (downloader.login().success()) {
                    var stats = downloader.getStatistics();
                    trafficJournalDao.updateData(downloader.getId(), stats.totalDownloaded(), stats.totalUploaded(), 0, 0);
                }
            } catch (Throwable e) {
                log.error("Unable to write hourly traffic journal to database", e);
            }
        }
        updateTrafficMonitoringService();
        updateTrafficCappingService();
    }

    private void updateTrafficCappingService() {
        if (!useTrafficSlidingCapping) {
            return;
        }
        for (Downloader downloader : downloaderManager) {
            try {
                if (downloader.login().success()) {
                    var speedLimiter = downloader.getSpeedLimiter();
                    if(speedLimiter == null) continue;
                    if(!downloader.getFeatureFlags().contains(DownloaderFeatureFlag.TRAFFIC_STATS)) continue;
                    var calculatedData = trafficJournalDao.tweakSpeedLimiterBySlidingWindow(null, speedLimiter, maxTrafficAllowedInWindowPeriod, trafficSlidingCappingMinSpeed, trafficSlidingCappingMaxSpeed);
                    DownloaderSpeedLimiter  newLimiter = new DownloaderSpeedLimiter(calculatedData.getNewSpeedLimit(), speedLimiter.download());
                    downloader.setSpeedLimiter(newLimiter);
                    if(Main.getMeta().isSnapshotOrBeta()) {
                        log.info(tlUI(Lang.MODULE_ACTIVE_MONITORING_SPEED_LIMITER_SLIDING_WINDOW_NEW_APPLIED, downloader.getName(), MsgUtil.humanReadableByteCountBin(newLimiter.upload()) + "/s", MsgUtil.humanReadableByteCountSI(newLimiter.upload()) + "/s", calculatedData));
                    }
                }
            } catch (Throwable e) {
                log.error("Unable to update traffic settings followed by sliding window", e);
            }
        }
    }

    @SneakyThrows
    private void updateTrafficMonitoringService() {
        if (dailyTrafficCapping <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        // Calculating the today traffic
        long startOfToday = MiscUtil.getStartOfToday(now);
        var data = trafficJournalDao.getTodayData(null);
        long totalBytes = data.getDataOverallUploaded();
        var dateTimeString = MiscUtil.formatDateTime(now);
        var dateString = MiscUtil.formatDateOnly(now);
        var identifier = "dataTrafficCapping-" + startOfToday;
        // 一天只发一次
        if (totalBytes < dailyTrafficCapping) {
            return;
        }
        if (!alertManager.identifierAlertExistsIncludeRead(identifier)) {
            alertManager.publishAlert(true,
                    AlertLevel.WARN,
                    identifier,
                    new TranslationComponent(Lang.MODULE_AMM_TRAFFIC_MONITORING_TRAFFIC_ALERT_TITLE, dateString),
                    new TranslationComponent(Lang.MODULE_AMM_TRAFFIC_MONITORING_TRAFFIC_ALERT_DESCRIPTION,
                            dateTimeString,
                            MsgUtil.humanReadableByteCountBin(totalBytes),
                            MsgUtil.humanReadableByteCountBin(dailyTrafficCapping)));
        }
    }

    public void flush() {
        try {
            try {
                diskWriteCache.asMap().forEach((k,v)->dataBuffer.offer(k));
                peerRecordDao.syncPendingTasks(dataBuffer, torrentDao);
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
        updateTrafficStatus();
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

    @Override
    public void onTorrentPeersRetrieved(@NotNull Downloader downloader, @NotNull Torrent torrent, @NotNull List<Peer> peers) {
        peers.stream().filter(peer -> {
                    var clientName = peer.getClientName();
                    var peerId = peer.getPeerId();
                    if (clientName != null && !clientName.isBlank()) {
                        return true;
                    }
                    if (peerId != null && !peerId.isBlank()) {
                        return true;
                    }
                    return !peer.isHandshaking();
                })
                .forEach(peer ->
                        diskWriteCache.put(new PeerRecordDao.BatchHandleTasks(System.currentTimeMillis(),
                                        downloader.getId(), new TorrentWrapper(torrent), new PeerWrapper(peer)),
                                MiscUtil.EMPTY_OBJECT));
    }
}
