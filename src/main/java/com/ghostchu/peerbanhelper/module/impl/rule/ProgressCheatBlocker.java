package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.database.dao.impl.ProgressCheatBlockerPersistDao;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;

@Component
@Slf4j
public class ProgressCheatBlocker extends AbstractRuleFeatureModule {
    private final Deque<ClientTaskRecord> pendingPersistQueue = new ConcurrentLinkedDeque<>();
    private final Cache<String, List<ClientTask>> progressRecorder = CacheBuilder.newBuilder()
            .maximumSize(3192)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .removalListener(notification -> {
                if (!notification.wasEvicted()) return;
                String key = (String) notification.getKey();
                @SuppressWarnings("unchecked")
                List<ClientTask> tasks = (List<ClientTask>) notification.getValue();
                pendingPersistQueue.offer(new ClientTaskRecord(key, tasks));
            })
            .build();
    private ScheduledExecutorService scheduledTimer;
    private long torrentMinimumSize;
    private boolean blockExcessiveClients;
    private double excessiveThreshold;
    private double maximumDifference;
    private double rewindMaximumDifference;
    private int ipv4PrefixLength;
    private int ipv6PrefixLength;
    @Autowired
    private JavalinWebContainer webContainer;
    private long banDuration;
    @Autowired
    private ProgressCheatBlockerPersistDao progressCheatBlockerPersistDao;
    private boolean enablePersist;
    private long persistDuration;

    @Override
    public @NotNull String getName() {
        return "Progress Cheat Blocker";
    }

    @Override
    public @NotNull String getConfigName() {
        return "progress-cheat-blocker";
    }

    @Override
    public void onEnable() {
        reloadConfig();
        webContainer.javalin()
                .get("/api/modules/" + getConfigName(), this::handleConfig, Role.USER_READ)
                .get("/api/modules/" + getConfigName() + "/status", this::handleStatus, Role.USER_READ);
        scheduledTimer = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
        scheduledTimer.scheduleWithFixedDelay(this::flushDatabase, 60, 60, TimeUnit.SECONDS);
        scheduledTimer.scheduleWithFixedDelay(this::cleanDatabase, 0, 8, TimeUnit.HOURS);
    }

    private void cleanDatabase() {
        try {
            progressCheatBlockerPersistDao.cleanupDatabase(new Timestamp(System.currentTimeMillis() - persistDuration));
        } catch (SQLException e) {
            log.error("Unable to remove expired data from database", e);
        }
    }

    private void flushDatabase() {
        List<ClientTaskRecord> records = new ArrayList<>();
        while (!pendingPersistQueue.isEmpty()) {
            records.add(pendingPersistQueue.poll());
        }
        try {
            progressCheatBlockerPersistDao.flushDatabase(records);
        } catch (SQLException e) {
            log.error("Unable flush records into database", e);
        }
    }

    private void handleStatus(Context ctx) {
        ctx.status(HttpStatus.OK);
        List<ClientTaskRecord> records = progressRecorder.asMap().entrySet().stream()
                .map(entry -> new ClientTaskRecord(entry.getKey(), entry.getValue()))
                .toList();
        ctx.json(records);
    }


    public void handleConfig(Context ctx) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("torrentMinimumSize", torrentMinimumSize);
        config.put("blockExcessiveClients", blockExcessiveClients);
        config.put("excessiveThreshold", excessiveThreshold);
        config.put("maximumDifference", maximumDifference);
        config.put("rewindMaximumDifference", rewindMaximumDifference);
        ctx.status(HttpStatus.OK);
        ctx.json(config);
    }

    @Override
    public boolean isThreadSafe() {
        return true;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }


    @Override
    public void onDisable() {
        scheduledTimer.shutdownNow();
        progressRecorder.cleanUp();
        if (enablePersist) {
            progressRecorder.asMap().forEach((k, v) -> pendingPersistQueue.add(new ClientTaskRecord(k, v)));
            flushDatabase();
        }
        progressRecorder.invalidateAll();
    }

    private void reloadConfig() {
        this.banDuration = getConfig().getLong("ban-duration", 0);
        this.torrentMinimumSize = getConfig().getLong("minimum-size");
        this.blockExcessiveClients = getConfig().getBoolean("block-excessive-clients");
        this.excessiveThreshold = getConfig().getDouble("excessive-threshold");
        this.maximumDifference = getConfig().getDouble("maximum-difference");
        this.rewindMaximumDifference = getConfig().getDouble("rewind-maximum-difference");
        this.ipv4PrefixLength = getConfig().getInt("ipv4-prefix-length");
        this.ipv6PrefixLength = getConfig().getInt("ipv6-prefix-length");
        this.enablePersist = getConfig().getBoolean("enable-persist");
        this.persistDuration = getConfig().getLong("persist-duration");
    }

    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        if (isHandShaking(peer)) {
            return handshaking();
        }
        // 处理 IPV6
        IPAddress peerIp;
        if (peer.getPeerAddress().getAddress().isIPv4()) {
            peerIp = IPAddressUtil.toPrefixBlock(peer.getPeerAddress().getAddress(), ipv4PrefixLength);
        } else {
            peerIp = IPAddressUtil.toPrefixBlock(peer.getPeerAddress().getAddress(), ipv6PrefixLength);
        }
        String peerIpString = peerIp.toString();
        // 从缓存取数据
        List<ClientTask> lastRecordedProgress = null;
        try {
            lastRecordedProgress = progressRecorder.get(peerIpString, () -> loadClientTasks(peerIpString, torrent.getId()));
        } catch (ExecutionException e) {
            log.error("Unhandled exception during load cached record data", e);
        }
        if (lastRecordedProgress == null) lastRecordedProgress = new CopyOnWriteArrayList<>();
        ClientTask clientTask = null;
        for (ClientTask recordedProgress : lastRecordedProgress) {
            if (recordedProgress.getTorrentId().equals(torrent.getId())) {
                clientTask = recordedProgress;
                break;
            }
        }
        if (clientTask == null) {
            clientTask = new ClientTask(torrent.getId(), 0d, 0L, 0L, 0, 0, System.currentTimeMillis(), System.currentTimeMillis());
            lastRecordedProgress.add(clientTask);
        }
        long uploadedIncremental; // 上传增量
        if (peer.getUploaded() < clientTask.getLastReportUploaded()) {
            uploadedIncremental = peer.getUploaded();
        } else {
            uploadedIncremental = peer.getUploaded() - clientTask.getLastReportUploaded();
        }
        // 累加上传增量
        clientTask.setTrackingUploadedIncreaseTotal(clientTask.getTrackingUploadedIncreaseTotal() + uploadedIncremental);
        // 获取真实已上传量（下载器报告、PBH上次报告记录，增量总计，三者取最大）
        final long actualUploaded = Math.max(peer.getUploaded(), Math.max(clientTask.getLastReportUploaded(), clientTask.getTrackingUploadedIncreaseTotal()));
        try {
            final long torrentSize = torrent.getSize();
            // 过滤
            if (torrentSize <= 0) {
                return pass();
            }
            if (torrentSize < torrentMinimumSize) {
                return pass();
            }
            // 计算进度信息
            final double actualProgress = (double) actualUploaded / torrentSize; // 实际进度
            final double clientProgress = peer.getProgress(); // 客户端汇报进度
            // actualUploaded = -1 代表客户端不支持统计此 Peer 总上传量
            if (actualUploaded != -1 && blockExcessiveClients && (actualUploaded > torrentSize)) {
                // 下载过量，检查
                long maxAllowedExcessiveThreshold = (long) (torrentSize * excessiveThreshold);
                if (actualUploaded > maxAllowedExcessiveThreshold) {
                    return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.PCB_RULE_REACHED_MAX_ALLOWED_EXCESSIVE_THRESHOLD),
                            new TranslationComponent(Lang.MODULE_PCB_EXCESSIVE_DOWNLOAD,
                                    torrentSize,
                                    actualUploaded,
                                    maxAllowedExcessiveThreshold));
                }
            }
            // 如果客户端报告自己进度更多，则跳过检查
            if (actualProgress <= clientProgress) {
                return pass();
            }
            // 计算进度差异
            double difference = Math.abs(actualProgress - clientProgress);
            if (difference > maximumDifference) {
                clientTask.setProgressDifferenceCounter(clientTask.getProgressDifferenceCounter() + 1);
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.PCB_RULE_REACHED_MAX_DIFFERENCE),
                        new TranslationComponent(Lang.MODULE_PCB_PEER_BAN_INCORRECT_PROGRESS,
                                percent(clientProgress),
                                percent(actualProgress),
                                percent(difference)));
            }
            if (rewindMaximumDifference > 0) {
                double lastRecord = clientTask.getLastReportProgress();
                double rewind = lastRecord - peer.getProgress();
                boolean ban = rewind > rewindMaximumDifference;
                if (ban) {
                    clientTask.setRewindCounter(clientTask.getRewindCounter() + 1);
                    progressRecorder.invalidate(peerIpString); // 封禁时，移除缓存
                }

                return new CheckResult(getClass(), ban ? PeerAction.BAN : PeerAction.NO_ACTION, 0, new TranslationComponent(Lang.PCB_RULE_PROGRESS_REWIND),
                        new TranslationComponent(Lang.MODULE_PCB_PEER_BAN_REWIND,
                                percent(clientProgress),
                                percent(actualProgress),
                                percent(lastRecord),
                                percent(rewind),
                                percent(rewindMaximumDifference)));
            }
            //return new CheckResult(getClass(), PeerAction.NO_ACTION, "N/A", String.format(Lang.MODULE_PCB_PEER_BAN_INCORRECT_PROGRESS, percent(clientProgress), percent(actualProgress), percent(difference)));
            return pass();
        } finally {
            // 无论如何都写入缓存，同步更改
            clientTask.setLastReportUploaded(peer.getUploaded());
            clientTask.setLastReportProgress(peer.getProgress());
            progressRecorder.put(peerIpString, lastRecordedProgress);
        }
    }

    private List<ClientTask> loadClientTasks(String peerIpString, String id) {
        try {
            if (enablePersist) {
                return progressCheatBlockerPersistDao.fetchFromDatabase(peerIpString, id, new Timestamp(System.currentTimeMillis() - persistDuration));
            }
        } catch (SQLException e) {
            log.error("Unable to load cached client tasks from database", e);
        }
        return new ArrayList<>();
    }


    private String percent(double d) {
        return MsgUtil.getPercentageFormatter().format(d);
    }

    public record ClientTaskRecord(String address, List<ClientTask> task) {
    }

    @AllArgsConstructor
    @Data
    public static class ClientTask {
        private String torrentId;
        private double lastReportProgress;
        private long lastReportUploaded;
        private long trackingUploadedIncreaseTotal;
        private int rewindCounter;
        private int progressDifferenceCounter;
        private long firstTimeSeen;
        private long lastTimeSeen;
    }
}


