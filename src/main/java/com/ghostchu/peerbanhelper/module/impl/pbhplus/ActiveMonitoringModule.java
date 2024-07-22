package com.ghostchu.peerbanhelper.module.impl.pbhplus;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.ghostchu.peerbanhelper.event.LivePeersUpdatedEvent;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.google.common.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public class ActiveMonitoringModule extends AbstractFeatureModule {
    private final PeerRecordDao peerRecordDao;
    private ExecutorService taskWriteService;
    private long dataRetentionTime;
    private long dataCleanupInterval;
    private ScheduledExecutorService cleanupScheduled;
    private BlockingQueue<Runnable> taskWriteQueue;

    public ActiveMonitoringModule(PeerRecordDao peerRecordDao) {
        super();
        this.peerRecordDao = peerRecordDao;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public @NotNull String getName() {
        return "PeerRecordModule (PBH+)";
    }

    @Override
    public @NotNull String getConfigName() {
        return "active-monitoring";
    }

    @Subscribe
    private void onLivePeerSnapshotEvent(LivePeersUpdatedEvent event) {
        List<PeerRecordDao.BatchHandleTasks> tasks = new ArrayList<>();
        event.getLivePeers().values().forEach(meta -> tasks.add(new PeerRecordDao.BatchHandleTasks(meta.getDownloader(),
                meta.getTorrent(), meta.getPeer())));
        taskWriteService.submit(() -> {
            try {
                peerRecordDao.syncPendingTasks(tasks);
            } catch (SQLException e) {
                log.warn("Unable sync peers data to database", e);
            }
        });

    }

    @Override
    public void onEnable() {
        reloadConfig();
        this.taskWriteQueue = new LinkedBlockingQueue<>();
        this.taskWriteService = new ThreadPoolExecutor(1, 2, 60L, TimeUnit.SECONDS, taskWriteQueue);
        Main.getEventBus().register(this);
    }

    private void reloadConfig() {
        this.taskWriteService = Executors.newVirtualThreadPerTaskExecutor();
        this.dataRetentionTime = getConfig().getLong("data-retention-time", -1);
        this.dataCleanupInterval = getConfig().getLong("data-cleanup-interval", -1);
        this.cleanupScheduled = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
        this.cleanupScheduled.scheduleWithFixedDelay(this::cleanup, 0, dataCleanupInterval, TimeUnit.MILLISECONDS);
    }

    private void cleanup() {
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
    }

    @Override
    public void onDisable() {
        Main.getEventBus().unregister(this);
        if (!this.cleanupScheduled.isShutdown()) {
            this.cleanupScheduled.shutdownNow();
        }
        taskWriteService.shutdown();
        try {
            log.info(tlUI(Lang.AMM_SHUTTING_DOWN));
            if (!taskWriteService.awaitTermination(5, TimeUnit.SECONDS)) {
                taskWriteService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
//    @AllArgsConstructor
//    @Getter
//    static class QueuedPeerRecord implements Comparable<QueuedPeerRecord> {
//        private String downloader;
//        private TorrentWrapper torrent;
//        private PeerWrapper peer;
//
//        /*
//            这里手动实现一下 hashCode 和 equals，因为我们要做Deque内数据替换
//         */
//        @Override
//        public int hashCode() {
//            return Objects.hash(downloader, torrent.getHash(), torrent.getName(), peer.getAddress().getIp(), peer.getAddress().getPort());
//        }
//
//        @Override
//        public boolean equals(Object obj) {
//            if(!(obj instanceof QueuedPeerRecord target)){
//                return false;
//            }
//            if(downloader.equals(target.downloader)){
//                if(torrent.getHash().equals(target.torrent.getHash())){
//                    if(peer.getAddress().getIp().equals(target.peer.getAddress().getIp())){
//                        return peer.getAddress().getPort() == target.peer.getAddress().getPort();
//                    }
//                }
//            }
//            return false;
//        }
//
//        @Override
//        public int compareTo(@NotNull ActiveMonitoringModule.QueuedPeerRecord o) {
//            return peer.toPeerAddress().compareTo(o.peer.toPeerAddress());
//        }
//    }
}
