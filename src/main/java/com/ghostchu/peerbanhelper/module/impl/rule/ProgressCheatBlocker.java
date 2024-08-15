package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
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
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Weigher;
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
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

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public class ProgressCheatBlocker extends AbstractRuleFeatureModule implements Reloadable {
    private final Deque<ClientTaskRecord> pendingPersistQueue = new ConcurrentLinkedDeque<>();
    private final Cache<String, List<ClientTask>> progressRecorder = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .weigher((Weigher<String, List<ClientTask>>) (key, value) -> {
                int totalSize = calcStringSize(key);
                for (ClientTask clientTask : value) {
                    totalSize += calcClientTaskSize(clientTask);
                }
                totalSize += 12;
                return totalSize;
            })
            .maximumWeight(12000000)
            .removalListener(notification -> {
                String key = notification.getKey();
                List<ClientTask> tasks = notification.getValue();
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
        scheduledTimer.scheduleWithFixedDelay(this::flushDatabase, 30, 30, TimeUnit.SECONDS);
        scheduledTimer.scheduleWithFixedDelay(this::cleanDatabase, 0, 8, TimeUnit.HOURS);
        Main.getReloadManager().register(this);
    }

    private void cleanDatabase() {
        try {
            progressCheatBlockerPersistDao.cleanupDatabase(new Timestamp(System.currentTimeMillis() - persistDuration));
        } catch (Throwable e) {
            log.error("Unable to remove expired data from database", e);
        }
    }

    private void flushDatabase() {
        try {
            List<ClientTaskRecord> records = new ArrayList<>();
            while (!pendingPersistQueue.isEmpty()) {
                records.add(pendingPersistQueue.poll());
            }
            try {
                progressCheatBlockerPersistDao.flushDatabase(records);
            } catch (SQLException e) {
                log.error("Unable flush records into database", e);
            }
        } catch (Throwable throwable) {
            log.error("Unable to complete scheduled tasks", throwable);
        }
    }

    private void handleStatus(Context ctx) {
        List<ClientTaskRecord> records = progressRecorder.asMap().entrySet().stream()
                .map(entry -> new ClientTaskRecord(entry.getKey(), entry.getValue()))
                .toList();
        ctx.json(new StdResp(true, null, records));
    }


    public void handleConfig(Context ctx) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("torrentMinimumSize", torrentMinimumSize);
        config.put("blockExcessiveClients", blockExcessiveClients);
        config.put("excessiveThreshold", excessiveThreshold);
        config.put("maximumDifference", maximumDifference);
        config.put("rewindMaximumDifference", rewindMaximumDifference);
        ctx.json(new StdResp(true, null, config));
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
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }

    @Override
    public void onDisable() {
        scheduledTimer.shutdown();
        progressRecorder.invalidateAll();
        if (enablePersist) {
            log.info(tlUI(Lang.PCB_SHUTTING_DOWN));
            flushDatabase();
        }
        Main.getReloadManager().unregister(this);
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
        IPAddress peerPrefix;
        if (peer.getPeerAddress().getAddress().isIPv4()) {
            peerPrefix = IPAddressUtil.toPrefixBlock(peer.getPeerAddress().getAddress(), ipv4PrefixLength);
        } else {
            peerPrefix = IPAddressUtil.toPrefixBlock(peer.getPeerAddress().getAddress(), ipv6PrefixLength);
        }
        String peerPrefixString = peerPrefix.toString();
        String peerIpString = peer.getPeerAddress().getAddress().toString();
        // 从缓存取数据
        List<ClientTask> lastRecordedProgress = null;
        try {
            lastRecordedProgress = progressRecorder.get(peerPrefixString, () -> loadClientTasks(peerPrefix, torrent.getId()));
        } catch (ExecutionException e) {
            log.error("Unhandled exception during load cached record data", e);
        }
        if (lastRecordedProgress == null) lastRecordedProgress = new CopyOnWriteArrayList<>();
        ClientTask clientTask = null;
        for (ClientTask recordedProgress : lastRecordedProgress) {
            if (recordedProgress.getPeerIp().equals(peerIpString) && recordedProgress.getTorrentId().equals(torrent.getId())) {
                clientTask = recordedProgress;
                break;
            }
        }
        if (clientTask == null) {
            clientTask = new ClientTask(peerIpString, torrent.getId(), 0d, 0L, 0L, 0, 0, System.currentTimeMillis(), System.currentTimeMillis());
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
        // 整个段的增量总计
        final long prefixTrackingUploadedIncreaseTotal = lastRecordedProgress.stream().mapToLong(task -> task.getTorrentId().equals(torrent.getId()) ? task.getTrackingUploadedIncreaseTotal() : 0).sum();
        // 获取真实已上传量（下载器报告、PBH上次报告记录，整个段的增量总计，三者取最大）
        final long actualUploaded = Math.max(peer.getUploaded(), Math.max(clientTask.getLastReportUploaded(), prefixTrackingUploadedIncreaseTotal));
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
            progressRecorder.put(peerPrefixString, lastRecordedProgress);
        }
    }

    private List<ClientTask> loadClientTasks(IPAddress peerPrefix, String id) {
        try {
            if (enablePersist) {
                return progressCheatBlockerPersistDao.fetchFromDatabase(peerPrefix, id, new Timestamp(System.currentTimeMillis() - persistDuration));
            }
        } catch (SQLException e) {
            log.error("Unable to load cached client tasks from database", e);
        }
        return new CopyOnWriteArrayList<>();
    }

    private int calcStringSize(String str) {
        return 40 + 2 * str.length();
    }

    private int calcClientTaskSize(ClientTask clientTask) {
        // long = 8
        // double = 8
        // int = 4
        // 对象头 = 12
        return calcStringSize(clientTask.peerIp) + calcStringSize(clientTask.torrentId) + (4 * 8) + 8 + (2 * 4) + 12;
    }

    private String percent(double d) {
        return MsgUtil.getPercentageFormatter().format(d);
    }

    public record ClientTaskRecord(String address, List<ClientTask> task) {
    }

    @AllArgsConstructor
    @Data
    public static class ClientTask {
        private String peerIp;
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


