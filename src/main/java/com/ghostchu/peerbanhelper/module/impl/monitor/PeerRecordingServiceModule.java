package com.ghostchu.peerbanhelper.module.impl.monitor;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.databasent.service.PeerRecordService;
import com.ghostchu.peerbanhelper.databasent.service.impl.common.PeerRecordServiceImpl;
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

    private final int bufferSize = Math.max(1, ExternalSwitch.parseInt("pbh.module.peerRecordingServiceModule.diskWriteCache.size", 3500));
    private final LinkedHashMap<CacheKey, PeerRecordServiceImpl.PeerRecordCachingEntire> pending = new LinkedHashMap<>();

    private long dataRetentionTime;
    @Autowired
    private BackgroundTaskManager backgroundTaskManager;

    public synchronized void flush() {
        if (pending.isEmpty()) return;
        peerRecordDao.flushToDatabase(pending.values());
        pending.clear();
    }

    private void flushSafely() {
        try {
            flush();
        } catch (RuntimeException e) {
            log.warn("Unable to flush peer records", e);
        }
    }

    private synchronized void record(Downloader downloader, TorrentWrapper torrent, Peer peer) {
        var address = peer.getPeerAddress();
        var key = new CacheKey(downloader.getId(), torrent.getHash(), address.getAddress().toInetAddress());
        var previous = pending.get(key);
        if (previous == null && pending.size() >= bufferSize) {
            flush();
        }
        var now = OffsetDateTime.now();
        var snapshot = new PeerWrapper(peer);
        if (previous == null) {
            pending.put(key, new PeerRecordServiceImpl.PeerRecordCachingEntire(now, downloader.getId(), torrent, snapshot));
        } else {
            previous.observe(now, snapshot);
        }
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void onPeersRetrieved(@NotNull Downloader downloader, Torrent torrent, List<? extends Peer> peers, @NotNull PipelineTask<?> task) {
        task.setComment(true, "Update Peers into diskWriteCache, and flush to disk if needed.");
        var torrentSnapshot = new TorrentWrapper(torrent);
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
                .forEach(peer -> record(downloader, torrentSnapshot, peer));
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
        long dataFlushInterval = getConfig().getLong("data-flush-interval", 900_000);
        CommonUtil.getBgCleanupScheduler().scheduleWithFixedDelay(this::cleanup, 0, dataCleanupInterval, TimeUnit.MILLISECONDS);
        registerScheduledTask(this::flushSafely, 0, Math.max(1, dataFlushInterval), TimeUnit.MILLISECONDS);
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
        try {
            flush();
        } catch (Exception e) {
            log.warn("Unable to close peer recording cache instance", e);
        }
    }

    private record CacheKey(String downloader, String infoHash, InetAddress address) {

    }
}
