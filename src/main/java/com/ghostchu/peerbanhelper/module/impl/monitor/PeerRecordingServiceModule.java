package com.ghostchu.peerbanhelper.module.impl.monitor;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.databasent.service.PeerRecordService;
import com.ghostchu.peerbanhelper.databasent.service.impl.common.PeerRecordServiceImpl.PeerRecordCachingEntire;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BatchMonitorFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTaskManager;
import com.ghostchu.peerbanhelper.util.backgroundtask.FunctionalBackgroundTask;
import com.ghostchu.peerbanhelper.wrapper.PeerWrapper;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public class PeerRecordingServiceModule extends AbstractFeatureModule implements Reloadable, BatchMonitorFeatureModule {
    @Autowired
    private PeerRecordService peerRecordDao;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final int capacity = Math.max(1,
            ExternalSwitch.parseInt("pbh.module.peerRecordingServiceModule.diskWriteCache.size", 3500));
    private final long maxDelay = ExternalSwitch.parseLong("pbh.module.peerRecordingServiceModule.diskWriteCache.timeout", 180000);
    private final LinkedHashMap<CacheKey, ObservationWindow> pending = new LinkedHashMap<>();
    private boolean accepting;
    private long dataRetentionTime;
    @Autowired
    private BackgroundTaskManager backgroundTaskManager;

    public synchronized void flush() {
        flushOldest(pending.size());
    }

    // Remove entries only after the transaction commits. A failed batch can be retried in order.
    private void flushOldest(int count) {
        if (count == 0) return;
        var batch = pending.values().stream().limit(count).toList();
        transactionTemplate.executeWithoutResult(_ -> {
            for (var window : batch) {
                peerRecordDao.flushToDatabase(window.first);
                if (window.last != window.first) peerRecordDao.flushToDatabase(window.last);
            }
        });
        for (int i = 0; i < count; i++) pending.pollFirstEntry();
    }

    private void scheduledFlush() {
        try {
            flush();
        } catch (RuntimeException e) {
            log.error("Unable to flush peer observations, will retry", e);
        }
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public synchronized void onPeersRetrieved(@NotNull Downloader downloader, Torrent torrent, List<? extends Peer> peers, @NotNull PipelineTask<?> task) {
        if (!accepting || peers.isEmpty()) return;
        task.setComment(false, "Recording peer observations");
        var snapshot = new TorrentWrapper(torrent);
        for (Peer peer : peers) {
            if ((peer.getClientName() == null || peer.getClientName().isBlank())
                    && (peer.getPeerId() == null || peer.getPeerId().isBlank()) && peer.isHandshaking()) continue;
            var key = new CacheKey(downloader.getId(), torrent.getHash(), peer.getPeerAddress().getAddress().toInetAddress());
            var observation = new PeerRecordCachingEntire(OffsetDateTime.now(), downloader.getId(), snapshot, new PeerWrapper(peer), true);
            var window = pending.get(key);
            if (window != null && window.canAppend(observation)) {
                window.last = observation;
                continue;
            }
            if (window != null) {
                // The SQL upsert computes deltas against the previous absolute counters. Persist both
                // endpoints before starting a new counter sequence (reconnect or counter reset).
                pending.putFirst(key, window);
                flushOldest(1);
            } else if (pending.size() >= capacity) {
                flushOldest(Math.max(1, capacity / 4));
            }
            pending.put(key, new ObservationWindow(observation));
        }
        if (maxDelay == 0) flush();
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
    public synchronized void onEnable() {
        reloadConfig();
        long dataCleanupInterval = getConfig().getLong("data-cleanup-interval", -1);
        long dataFlushInterval = getConfig().getLong("data-flush-interval", 20000);
        CommonUtil.getBgCleanupScheduler().scheduleWithFixedDelay(this::cleanup, 0, dataCleanupInterval, TimeUnit.MILLISECONDS);
        accepting = true;
        long flushInterval = maxDelay > 0 ? Math.min(dataFlushInterval, maxDelay) : dataFlushInterval;
        registerScheduledTask(this::scheduledFlush, 0, Math.max(1, flushInterval), TimeUnit.MILLISECONDS);
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
    public synchronized void onDisable() {
        accepting = false;
        flush();
    }

    // Matches the database conflict key. A port change starts a new observation window.
    private record CacheKey(String downloader, String torrentHash, InetAddress address) {}

    private static final class ObservationWindow {
        final PeerRecordCachingEntire first;
        PeerRecordCachingEntire last;

        ObservationWindow(PeerRecordCachingEntire first) {
            this.first = this.last = first;
        }

        boolean canAppend(PeerRecordCachingEntire next) {
            var previous = last.getPeer();
            var current = next.getPeer();
            return previous.getAddress().getPort() == current.getAddress().getPort()
                    && previous.getUploaded() <= current.getUploaded()
                    && previous.getDownloaded() <= current.getDownloaded();
        }
    }
}
