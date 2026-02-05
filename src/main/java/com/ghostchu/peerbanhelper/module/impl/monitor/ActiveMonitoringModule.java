package com.ghostchu.peerbanhelper.module.impl.monitor;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.databasent.service.TrafficJournalService;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderFeatureFlag;
import com.ghostchu.peerbanhelper.downloader.DownloaderManagerImpl;
import com.ghostchu.peerbanhelper.downloader.DownloaderSpeedLimiter;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.MonitorFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.ghostchu.peerbanhelper.util.TimeUtil;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import io.sentry.Sentry;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public final class ActiveMonitoringModule extends AbstractFeatureModule implements Reloadable, MonitorFeatureModule {
    @Autowired
    private TrafficJournalService trafficJournalDao;
    @Autowired
    private AlertManager alertManager;
    @Autowired
    private DownloaderManagerImpl downloaderManager;
    private long dailyTrafficCapping;
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
        registerScheduledTask(this::updateTrafficStatus, 0, 1, TimeUnit.MINUTES);
        Main.getReloadManager().register(this);
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }

    private void reloadConfig() {
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
                Sentry.captureException(e);
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
                    DownloaderSpeedLimiter newLimiter = new DownloaderSpeedLimiter(calculatedData.getNewSpeedLimit(), speedLimiter.download());
                    downloader.setSpeedLimiter(newLimiter);
                    if(Main.getMeta().isSnapshotOrBeta()) {
                        log.info(tlUI(Lang.MODULE_ACTIVE_MONITORING_SPEED_LIMITER_SLIDING_WINDOW_NEW_APPLIED, downloader.getName(), MsgUtil.humanReadableByteCountBin(newLimiter.upload()) + "/s", MsgUtil.humanReadableByteCountSI(newLimiter.upload()) + "/s", calculatedData));
                    }
                }
            } catch (Throwable e) {
                log.error("Unable to update traffic settings followed by sliding window", e);
                Sentry.captureException(e);
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
        long startOfToday = TimeUtil.getStartOfToday(now).toEpochSecond();
        var data = trafficJournalDao.getTodayData(null);
        long totalBytes = data.getDataOverallUploaded();
        var dateTimeString = TimeUtil.formatDateTime(now);
        var dateString = TimeUtil.formatDateOnly(now);
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



    @Override
    public void onDisable() {
        Main.getEventBus().unregister(this);
        updateTrafficStatus();
        Main.getReloadManager().unregister(this);
    }

    @Override
    public void onTorrentPeersRetrieved(@NotNull Downloader downloader, @NotNull Torrent torrent, @NotNull List<Peer> peers) {

    }
}
