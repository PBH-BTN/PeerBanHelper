package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.ghostchu.peerbanhelper.event.LivePeersUpdatedEvent;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.wrapper.PeerMetadata;
import com.google.common.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public class ActiveMonitoringModule extends AbstractFeatureModule {
    private final PeerRecordDao peerRecordDao;
    private final Set<ReplaceableMonitoringData> dataBuffer = new CopyOnWriteArraySet<>();
    private ExecutorService taskWriteService;
    private long dataRetentionTime;
    private ScheduledExecutorService scheduleService;
    @SuppressWarnings("FieldCanBeLocal")
    private BlockingQueue<Runnable> taskWriteQueue;
    private boolean shouldSync = false;

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
        return "Active Monitoring";
    }

    @Override
    public @NotNull String getConfigName() {
        return "active-monitoring";
    }

    @Subscribe
    private void onLivePeerSnapshotEvent(LivePeersUpdatedEvent event) {
        List<ReplaceableMonitoringData> peerMetadatas = event.getLivePeers().values().stream().flatMap(Collection::stream)
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
                .map(ReplaceableMonitoringData::new)
                .toList();
        peerMetadatas.forEach(dataBuffer::remove);
        dataBuffer.addAll(peerMetadatas);
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
        long dataCleanupInterval = getConfig().getLong("data-cleanup-interval", -1);
        this.scheduleService = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
        this.scheduleService.scheduleWithFixedDelay(this::cleanup, 0, dataCleanupInterval, TimeUnit.MILLISECONDS);
        this.scheduleService.scheduleWithFixedDelay(this::flush, 20, 20, TimeUnit.SECONDS);
    }

    private void flush() {
        shouldSync = !shouldSync;
        if (!shouldSync) {
            if (dataBuffer.size() < 5000) {
                return;
            }
        }
        var it = dataBuffer.iterator();
        List<PeerRecordDao.BatchHandleTasks> tasks = new ArrayList<>();
        while (it.hasNext()) {
            var meta = it.next().metadata();
            tasks.add(new PeerRecordDao.BatchHandleTasks(meta.getDownloader(),
                    meta.getTorrent(), meta.getPeer()));
            it.remove();
        }
        taskWriteService.submit(() -> {
            try {
                peerRecordDao.syncPendingTasks(tasks);
            } catch (SQLException e) {
                log.warn("Unable sync peers data to database", e);
            }
        });
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
        if (!this.scheduleService.isShutdown()) {
            this.scheduleService.shutdownNow();
        }
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
    }

    public record ReplaceableMonitoringData(PeerMetadata metadata) {
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ReplaceableMonitoringData another)) {
                return false;
            }
            if (!(metadata.getPeer().toPeerAddress().equals(another.metadata().getPeer().toPeerAddress()))) {
                return false;
            }
            if (!(metadata.getDownloader().equals(another.metadata.getDownloader()))) {
                return false;
            }
            return metadata.getTorrent().equals(another.metadata().getTorrent());
        }

        @Override
        public int hashCode() {
            return Objects.hash(metadata.getPeer().getAddress(), metadata.getDownloader(), metadata.getTorrent(), metadata);
        }
    }
}
