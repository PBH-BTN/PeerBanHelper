package com.ghostchu.peerbanhelper.module.impl.monitor;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.ghostchu.peerbanhelper.database.dao.impl.TorrentDao;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.MonitorFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.wrapper.PeerWrapper;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public class PeerRecodingServiceModule extends AbstractFeatureModule implements Reloadable, MonitorFeatureModule {
    @Autowired
    private PeerRecordDao peerRecordDao;
    private final Deque<PeerRecordDao.BatchHandleTasks> dataBuffer = new ConcurrentLinkedDeque<>();
    private final BlockingDeque<Runnable> taskWriteQueue = new LinkedBlockingDeque<>();
    private final Cache<PeerRecordDao.@NotNull BatchHandleTasks, @NotNull Object> diskWriteCache = CacheBuilder
            .newBuilder()
            .expireAfterWrite(ExternalSwitch.parseLong("pbh.module.peerRecordingServiceModule.diskWriteCache.timeout", 180000), TimeUnit.MILLISECONDS)
            .maximumSize(ExternalSwitch.parseInt("pbh.module.peerRecordingServiceModule.diskWriteCache.size", 3500))
            .removalListener(notification -> dataBuffer.offer((PeerRecordDao.BatchHandleTasks) notification.getKey()))
            .build();

    private ExecutorService taskWriteService;
    private long dataRetentionTime;
    @Autowired
    private TorrentDao torrentDao;

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
                        diskWriteCache.put(new PeerRecordDao.BatchHandleTasks(System.currentTimeMillis(),
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
        this.taskWriteService = new ThreadPoolExecutor(1, 2, 60L, TimeUnit.SECONDS, taskWriteQueue);
        long dataCleanupInterval = getConfig().getLong("data-cleanup-interval", -1);
        long dataFlushInterval = getConfig().getLong("data-flush-interval", 20000);
        registerScheduledTask(this::cleanup, 0, dataCleanupInterval, TimeUnit.MILLISECONDS);
        registerScheduledTask(this::flush, 0, dataFlushInterval, TimeUnit.MILLISECONDS);
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
    }

    public void flush() {
        try {
            try {
                diskWriteCache.asMap().forEach((k, v) -> dataBuffer.offer(k));
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
            log.info(tlUI(Lang.PEER_RECORDING_SERVICE_CLEANING_UP));
            try {
                var deleteBuilder = peerRecordDao.deleteBuilder();
                var where = deleteBuilder.where().lt("lastTimeSeen", new Timestamp(System.currentTimeMillis() - dataRetentionTime));
                deleteBuilder.setWhere(where);
                int deleted = deleteBuilder.delete();
                log.info(tlUI(Lang.PEER_RECORDING_SERVICE_CLEANED_UP, deleted));
            } catch (SQLException e) {
                log.warn("Unable to clean up peer_records tables", e);
            }
        } catch (Throwable throwable) {
            log.error("Unable to complete scheduled tasks", throwable);
        }
    }

    @Override
    public void onDisable() {
        diskWriteCache.invalidateAll();
        flush();
        taskWriteService.shutdown();
        try {
            if (!taskWriteService.awaitTermination(10, TimeUnit.SECONDS)) {
                taskWriteService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
