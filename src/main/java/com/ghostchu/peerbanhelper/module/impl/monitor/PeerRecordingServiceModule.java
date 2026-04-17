package com.ghostchu.peerbanhelper.module.impl.monitor;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.databasent.service.PeerRecordService;
import com.ghostchu.peerbanhelper.databasent.service.impl.common.PeerRecordServiceImpl;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.MonitorFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTaskManager;
import com.ghostchu.peerbanhelper.util.backgroundtask.FunctionalBackgroundTask;
import com.ghostchu.peerbanhelper.wrapper.PeerWrapper;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public class PeerRecordingServiceModule extends AbstractFeatureModule implements Reloadable, MonitorFeatureModule {
    private final AtomicBoolean databaseBackFlushFlag = new AtomicBoolean(true);
    @Autowired
    private PeerRecordService peerRecordDao;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @SuppressWarnings("NullableProblems")
    private final Cache<PeerRecordServiceImpl.@NotNull BatchHandleTasks, @NotNull Object> diskWriteCache = CacheBuilder
            .newBuilder()
            .expireAfterWrite(ExternalSwitch.parseLong("pbh.module.peerRecordingServiceModule.diskWriteCache.timeout", 180000), TimeUnit.MILLISECONDS)
            .maximumSize(ExternalSwitch.parseInt("pbh.module.peerRecordingServiceModule.diskWriteCache.size", 3500))
            .removalListener((RemovalListener<PeerRecordServiceImpl.BatchHandleTasks, Object>) notification -> {
                //noinspection ConstantValue
                if (notification.getValue() == null) return; // OOM could be null
                if (!databaseBackFlushFlag.get()) return;
                backFlushDatabase(notification.getKey());
            })
            .build();

    private long dataRetentionTime;
    @Autowired
    private BackgroundTaskManager backgroundTaskManager;

    public void flush() {
        transactionTemplate.execute(_ -> {
            for (Map.Entry<PeerRecordServiceImpl.BatchHandleTasks, @NotNull Object> entry : diskWriteCache.asMap().entrySet()) {
                backFlushDatabase(entry.getKey());
            }
            return null;
        });
    }

    private void backFlushDatabase(PeerRecordServiceImpl.BatchHandleTasks key) {
        peerRecordDao.flushToDatabase(key);
    }


    @Override
    public boolean isConfigurable() {
        return true;
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
                        diskWriteCache.put(new PeerRecordServiceImpl.BatchHandleTasks(OffsetDateTime.now(),
                                        downloader.getId(), new TorrentWrapper(torrent), new PeerWrapper(peer)),
                                MiscUtil.EMPTY_OBJECT));
    }

    @Override
    public @NotNull String getName() {
        return "Peer Recording Service";
    }

    @Override
    public @NotNull String getConfigName() {
        return "peer-analyse-service.peer-recording";
    }

    @Override
    public void onEnable() {
        reloadConfig();
        long dataCleanupInterval = getConfig().getLong("data-cleanup-interval", -1);
        long dataFlushInterval = getConfig().getLong("data-flush-interval", 20000);
        CommonUtil.getBgCleanupScheduler().scheduleWithFixedDelay(this::cleanup, 0, dataCleanupInterval, TimeUnit.MILLISECONDS);
        registerScheduledTask(this::flush, 0, dataFlushInterval, TimeUnit.MILLISECONDS);
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }

    private void reloadConfig() {
        this.dataRetentionTime = getConfig().getLong("data-retention-time", -1);
    }


    private void cleanup() {
        try {
            if (dataRetentionTime <= 0) {
                return;
            }
            backgroundTaskManager.addTaskAsync(new FunctionalBackgroundTask(
                    new TranslationComponent(Lang.MODULE_PEER_RECORDING_DELETING_EXPIRED_DATA),
                    (_, _) -> {
                        log.info(tlUI(Lang.PEER_RECORDING_SERVICE_CLEANING_UP));
                        OffsetDateTime beforeAt = OffsetDateTime.now().minus(dataRetentionTime, ChronoUnit.MILLIS);
                        long deleted = peerRecordDao.cleanup(beforeAt);
                        log.info(tlUI(Lang.PEER_RECORDING_SERVICE_CLEANED_UP, deleted));
                    }
            )).join();
        } catch (Throwable throwable) {
            log.error("Unable to complete scheduled tasks", throwable);
            Sentry.captureException(throwable);
        }
    }

    @Override
    public void onDisable() {
        flush();
        databaseBackFlushFlag.set(false);
        diskWriteCache.invalidateAll();
        databaseBackFlushFlag.set(true);
    }
}
