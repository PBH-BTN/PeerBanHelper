package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.database.dao.impl.ProgressCheatBlockerPersistDao;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderFeatureFlag;
import com.ghostchu.peerbanhelper.event.PeerUnbanEvent;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Weigher;
import com.google.common.eventbus.Subscribe;
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
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
@IgnoreScan
public final class ProgressCheatBlocker extends AbstractRuleFeatureModule implements Reloadable {
    private final Deque<ClientTaskRecord> pendingPersistQueue = new ConcurrentLinkedDeque<>();
    private final Cache<Client, List<ClientTask>> progressRecorder = CacheBuilder.newBuilder()
            .expireAfterAccess(ExternalSwitch.parseLong("pbh.module.progressCheatBlocker.recorder.timeout", 1800000), TimeUnit.MILLISECONDS)
            .weigher((Weigher<Client, List<ClientTask>>) (key, value) -> {
                int totalSize = calcClientSize(key);
                for (ClientTask clientTask : value) {
                    totalSize += calcClientTaskSize(clientTask);
                }
                totalSize += 12;
                return totalSize;
            })
            .maximumWeight(ExternalSwitch.parseLong("pbh.module.progressCheatBlocker.recorder.weight", 12000000))
            .removalListener(notification -> {
                Client key = notification.getKey();
                List<ClientTask> tasks = notification.getValue();
                pendingPersistQueue.offer(new ClientTaskRecord(key, tasks));
            })
            .build();
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
    private long maxWaitDuration;
    private long fastPcbTestBlockingDuration;
    private double fastPcbTestPercentage;

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
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::flushDatabase, 30, 30, TimeUnit.SECONDS);
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::cleanDatabase, 0, 8, TimeUnit.HOURS);
        Main.getReloadManager().register(this);
    }

    @Subscribe
    public void onPeerUnBan(PeerUnbanEvent event) {
        IPAddress peerPrefix;
        IPAddress peerIp = event.getPeer().getAddress();
        if (peerIp.isIPv4()) {
            peerPrefix = IPAddressUtil.toPrefixBlock(peerIp, ipv4PrefixLength);
        } else {
            peerPrefix = IPAddressUtil.toPrefixBlock(peerIp, ipv6PrefixLength);
        }
        String peerIpString = peerIp.toString();
        Client client = new Client(peerPrefix.toString(), event.getBanMetadata().getTorrent().getId());
        // 从缓存取数据
        List<ClientTask> lastRecordedProgress = null;
        try {
            // 如果未命中缓存 只导入当前种子记录的ip
            lastRecordedProgress = progressRecorder.get(client, () -> loadClientTasks(client));
        } catch (ExecutionException e) {
            log.error("Unhandled exception during load cached record data", e);
        }
        if (lastRecordedProgress == null) lastRecordedProgress = new CopyOnWriteArrayList<>();
        ClientTask clientTask = lastRecordedProgress.stream().filter(task -> task.getPeerIp().equals(peerIpString)).findFirst().orElse(null);
        if (clientTask != null) {
            clientTask.setBanDelayWindowEndAt(0L);
            clientTask.setLastReportProgress(0);
            clientTask.setLastReportUploaded(0);
            clientTask.setTrackingUploadedIncreaseTotal(0);
            clientTask.setRewindCounter(0);
            clientTask.setProgressDifferenceCounter(0);
            clientTask.setFastPcbTestExecuteAt(0);
            progressRecorder.put(client, lastRecordedProgress);
        }
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
            try {
                progressCheatBlockerPersistDao.flushDatabase(pendingPersistQueue);
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
        this.maxWaitDuration = getConfig().getLong("max-wait-duration");
        this.fastPcbTestPercentage = getConfig().getDouble("fast-pcb-test-percentage");
        this.fastPcbTestBlockingDuration = getConfig().getLong("fast-pcb-test-block-duration");
        getCache().invalidateAll();
    }


    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull ExecutorService ruleExecuteExecutor) {
        if (isHandShaking(peer)) {
            return handshaking();
        }
        // 处理 IPV6
        IPAddress peerPrefix;
        IPAddress peerIp = peer.getPeerAddress().getAddress();
        if (peerIp.isIPv4()) {
            peerPrefix = IPAddressUtil.toPrefixBlock(peerIp, ipv4PrefixLength);
        } else {
            peerPrefix = IPAddressUtil.toPrefixBlock(peerIp, ipv6PrefixLength);
        }
        String peerIpString = peerIp.toString();
        Client client = new Client(peerPrefix.toString(), torrent.getId());
        // 从缓存取数据
        List<ClientTask> lastRecordedProgress = null;
        try {
            // 如果未命中缓存 只导入当前种子记录的ip
            lastRecordedProgress = progressRecorder.get(client, () -> loadClientTasks(client));
        } catch (ExecutionException e) {
            log.error("Unhandled exception during load cached record data", e);
        }
        if (lastRecordedProgress == null) lastRecordedProgress = new CopyOnWriteArrayList<>();
        ClientTask clientTask = lastRecordedProgress.stream().filter(task -> task.getPeerIp().equals(peerIpString)).findFirst().orElse(null);
        if (clientTask == null) {
            clientTask = new ClientTask(peerIpString, 0d, 0L, 0L, 0, 0, System.currentTimeMillis(), System.currentTimeMillis(), downloader.getName(), 0L, 0L);
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
        final long prefixTrackingUploadedIncreaseTotal = lastRecordedProgress.stream().mapToLong(ClientTask::getTrackingUploadedIncreaseTotal).sum();
        // 获取真实已上传量（下载器报告、PBH上次报告记录，整个段的增量总计，三者取最大）
        final long actualUploaded = Math.max(peer.getUploaded(), Math.max(clientTask.getLastReportUploaded(), prefixTrackingUploadedIncreaseTotal));
        try {
            final long torrentSize = torrent.getSize();
            final long completedSize = torrent.getCompletedSize();
            // 过滤
            if (torrentSize <= 0) {
                return pass();
            }

            // 是否需要进行快速 PCB 测试
            if (fastPcbTestPercentage > 0 && !fileTooSmall(torrentSize) && downloader.getFeatureFlags().contains(DownloaderFeatureFlag.UNBAN_IP)) {
                // 只在 <= 0（也就是从未测试过）的情况下对其进行测试
                if (clientTask.getFastPcbTestExecuteAt() <= 0) {
                    // 如果上传量大于设置的比率，我们主动断开一次连接，封禁 Peer 一段时间，并尽快解除封禁
                    if (actualUploaded >= fastPcbTestPercentage * torrentSize) {
                        clientTask.setFastPcbTestExecuteAt(actualUploaded);
                        return new CheckResult(getClass(), PeerAction.BAN_FOR_DISCONNECT, fastPcbTestBlockingDuration,
                                new TranslationComponent(Lang.PCB_RULE_PEER_PROGRESS_CHEAT_TESTING),
                                new TranslationComponent(Lang.PCB_DESCRIPTION_PEER_PROGRESS_CHEAT_TESTING)
                        );
                    }
                }
            }

            // 计算进度信息
            final double actualProgress = (double) actualUploaded / torrentSize; // 实际进度
            final double clientProgress = peer.getProgress(); // 客户端汇报进度
            // actualUploaded = -1 代表客户端不支持统计此 Peer 总上传量
            if (actualUploaded != -1 && blockExcessiveClients) {
                if (actualUploaded > torrentSize) {
                    // 下载量超过种子大小，检查
                    long maxAllowedExcessiveThreshold = (long) (torrentSize * excessiveThreshold);
                    if (actualUploaded > maxAllowedExcessiveThreshold) {
                        clientTask.setBanDelayWindowEndAt(0L);
                        progressRecorder.invalidate(client); // 封禁时，移除缓存
                        return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.PCB_RULE_REACHED_MAX_ALLOWED_EXCESSIVE_THRESHOLD),
                                new TranslationComponent(Lang.MODULE_PCB_EXCESSIVE_DOWNLOAD,
                                        torrentSize,
                                        actualUploaded,
                                        maxAllowedExcessiveThreshold));
                    }
                } else if (ExternalSwitch.parse("pbh.pcb.disable-completed-excessive") == null && completedSize > 0 && actualUploaded > completedSize) {
                    // 下载量超过任务大小，检查
                    long maxAllowedExcessiveThreshold = (long) (completedSize * excessiveThreshold);
                    if (actualUploaded > maxAllowedExcessiveThreshold) {
                        clientTask.setBanDelayWindowEndAt(0L);
                        progressRecorder.invalidate(client); // 封禁时，移除缓存
                        return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.PCB_RULE_REACHED_MAX_ALLOWED_EXCESSIVE_THRESHOLD),
                                new TranslationComponent(Lang.MODULE_PCB_EXCESSIVE_DOWNLOAD_INCOMPLETE,
                                        torrentSize,
                                        completedSize,
                                        actualUploaded,
                                        maxAllowedExcessiveThreshold));
                    }
                }
            }
            // 如果客户端报告自己进度更多，则跳过检查
            if (actualProgress <= clientProgress) {
                return pass();
            }
            // 计算进度差异
            double difference = Math.abs(actualProgress - clientProgress);
            // isUploadingToPeer 是为了确认下载器再给对方上传数据，因为对方使用 “超级做种” 时汇报的进度可能并不准确
            if (difference > maximumDifference && !fileTooSmall(torrentSize) && isUploadingToPeer(peer)) {
                if (banPeerIfConditionReached(clientTask)) {
                    clientTask.setProgressDifferenceCounter(clientTask.getProgressDifferenceCounter() + 1);
                    clientTask.setBanDelayWindowEndAt(0L);
                    progressRecorder.invalidate(client); // 封禁时，移除缓存
                    return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.PCB_RULE_REACHED_MAX_DIFFERENCE),
                            new TranslationComponent(Lang.MODULE_PCB_PEER_BAN_INCORRECT_PROGRESS,
                                    percent(clientProgress),
                                    percent(actualProgress),
                                    percent(difference)));
                }

            }
            if (rewindMaximumDifference > 0 && !fileTooSmall(torrentSize)) {
                double lastRecord = clientTask.getLastReportProgress();
                double rewind = lastRecord - peer.getProgress();
                // isUploadingToPeer 是为了确认下载器再给对方上传数据，因为对方使用 “超级做种” 时汇报的进度可能并不准确
                boolean ban = rewind > rewindMaximumDifference && isUploadingToPeer(peer);
                if (ban) {
                    clientTask.setRewindCounter(clientTask.getRewindCounter() + 1);
                    clientTask.setBanDelayWindowEndAt(0L);
                    progressRecorder.invalidate(client); // 封禁时，移除缓存
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
            progressRecorder.put(client, lastRecordedProgress);
        }
    }

    private boolean isUploadingToPeer(Peer peer) {
        return peer.getUploadSpeed() > 0 || peer.getUploaded() > 0;
    }

    private boolean fileTooSmall(long torrentSize) {
        return torrentSize < torrentMinimumSize;
    }

    private boolean banPeerIfConditionReached(ClientTask clientTask) {
        if (clientTask.getBanDelayWindowEndAt() == 0L) {
            clientTask.setBanDelayWindowEndAt(System.currentTimeMillis() + this.maxWaitDuration);
            return false;
        }
        return System.currentTimeMillis() >= clientTask.getBanDelayWindowEndAt();
    }

    private List<ClientTask> loadClientTasks(Client client) {
        try {
            if (enablePersist) {
                return progressCheatBlockerPersistDao.fetchFromDatabase(client, new Timestamp(System.currentTimeMillis() - persistDuration));
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
        return calcStringSize(clientTask.peerIp) + (7 * 8) + (2 * 4) + 12;
    }

    private int calcClientSize(Client client) {
        return calcStringSize(client.peerPrefix) + calcStringSize(client.torrentId) + 12;
    }

    private String percent(double d) {
        return MsgUtil.getPercentageFormatter().format(d);
    }

    public record ClientTaskRecord(Client client, List<ClientTask> task) {
    }

    @AllArgsConstructor
    @Data
    public static class ClientTask {
        private String peerIp;
        private double lastReportProgress;
        private long lastReportUploaded;
        private long trackingUploadedIncreaseTotal;
        private int rewindCounter;
        private int progressDifferenceCounter;
        private long firstTimeSeen;
        private long lastTimeSeen;
        private String downloader;
        private long banDelayWindowEndAt;
        private long fastPcbTestExecuteAt;
    }

    @AllArgsConstructor
    @Data
    public static class Client {
        private String peerPrefix;
        private String torrentId;
    }
}


