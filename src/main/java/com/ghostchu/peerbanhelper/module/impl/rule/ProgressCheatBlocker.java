package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class ProgressCheatBlocker extends AbstractRuleFeatureModule {
    private Cache<String, List<ClientTask>> progressRecorder;
    private long torrentMinimumSize;
    private boolean blockExcessiveClients;
    private double excessiveThreshold;
    private double maximumDifference;
    private double rewindMaximumDifference;
    private int ipv4PrefixLength;
    private int ipv6PrefixLength;
    @Autowired
    private JavalinWebContainer webContainer;
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

    }

    private void reloadConfig() {
        this.progressRecorder = CacheBuilder.newBuilder()
                .maximumSize(2048)
                .expireAfterWrite(getServer().getBanDuration(), TimeUnit.MILLISECONDS)
                .softValues()
                .build();
        this.torrentMinimumSize = getConfig().getLong("minimum-size");
        this.blockExcessiveClients = getConfig().getBoolean("block-excessive-clients");
        this.excessiveThreshold = getConfig().getDouble("excessive-threshold");
        this.maximumDifference = getConfig().getDouble("maximum-difference");
        this.rewindMaximumDifference = getConfig().getDouble("rewind-maximum-difference");
        this.ipv4PrefixLength = getConfig().getInt("ipv4-prefix-length");
        this.ipv6PrefixLength = getConfig().getInt("ipv6-prefix-length");
    }

    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        if (isHandShaking(peer)) {
            return handshaking();
        }
        // 处理 IPV6
        IPAddress peerIp;
        if (peer.getPeerAddress().getAddress().isIPv4()) {
            peerIp = peer.getPeerAddress().getAddress().toPrefixBlock(ipv4PrefixLength);
        } else {
            peerIp = peer.getPeerAddress().getAddress().toPrefixBlock(ipv6PrefixLength);
        }
        String peerIpString = peerIp.toString();
        // 从缓存取数据
        List<ClientTask> lastRecordedProgress = progressRecorder.getIfPresent(peerIpString);
        if (lastRecordedProgress == null) lastRecordedProgress = new CopyOnWriteArrayList<>();
        ClientTask clientTask = null;
        for (ClientTask recordedProgress : lastRecordedProgress) {
            if (recordedProgress.getTorrentId().equals(torrent.getId())) {
                clientTask = recordedProgress;
                break;
            }
        }
        if (clientTask == null) {
            clientTask = new ClientTask(torrent.getId(), 0d, 0L, 0L);
            lastRecordedProgress.add(clientTask);
        }
        long uploadedIncremental; // 上传增量
        if (peer.getUploaded() < clientTask.getLastReportUploaded()) {
            uploadedIncremental = peer.getUploaded();
            ;
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
                return new CheckResult(getClass(), PeerAction.NO_ACTION, "N/A", Lang.MODULE_PCB_SKIP_UNKNOWN_SIZE_TORRENT);
            }
            if (torrentSize < torrentMinimumSize) {
                return new CheckResult(getClass(), PeerAction.NO_ACTION, "N/A", "Skip due the torrent size");
            }
            // 计算进度信息
            final double actualProgress = (double) actualUploaded / torrentSize; // 实际进度
            final double clientProgress = peer.getProgress(); // 客户端汇报进度
            // actualUploaded = -1 代表客户端不支持统计此 Peer 总上传量
            if (actualUploaded != -1 && blockExcessiveClients && (actualUploaded > torrentSize)) {
                // 下载过量，检查
                long maxAllowedExcessiveThreshold = (long) (torrentSize * excessiveThreshold);
                if (actualUploaded > maxAllowedExcessiveThreshold) {
                    return new CheckResult(getClass(), PeerAction.BAN, "Max allowed excessive threshold: " + maxAllowedExcessiveThreshold, String.format(Lang.MODULE_PCB_EXCESSIVE_DOWNLOAD, torrentSize, actualUploaded, maxAllowedExcessiveThreshold));
                }
            }
            // 如果客户端报告自己进度更多，则跳过检查
            if (actualProgress <= clientProgress) {
                return new CheckResult(getClass(), PeerAction.NO_ACTION, "N/A", String.format(Lang.MODULE_PCB_PEER_MORE_THAN_LOCAL_SKIP, percent(clientProgress), percent(actualProgress)));
            }
            // 计算进度差异
            double difference = Math.abs(actualProgress - clientProgress);
            if (difference > maximumDifference) {
                return new CheckResult(getClass(), PeerAction.BAN, "Over max Difference: " + difference + " Details: " + clientTask, String.format(Lang.MODULE_PCB_PEER_BAN_INCORRECT_PROGRESS, percent(clientProgress), percent(actualProgress), percent(difference)));
            }
            if (rewindMaximumDifference > 0) {
                double lastRecord = clientTask.getLastReportProgress();
                double rewind = lastRecord - peer.getProgress();
                boolean ban = rewind > rewindMaximumDifference;
                return new CheckResult(getClass(), ban ? PeerAction.BAN : PeerAction.NO_ACTION, "RewindAllow: " + rewindMaximumDifference + " Details: " + clientTask, String.format(Lang.MODULE_PCB_PEER_BAN_REWIND, percent(clientProgress), percent(actualProgress), percent(lastRecord), percent(rewind), percent(rewindMaximumDifference)));
            }
            return new CheckResult(getClass(), PeerAction.NO_ACTION, "N/A", String.format(Lang.MODULE_PCB_PEER_BAN_INCORRECT_PROGRESS, percent(clientProgress), percent(actualProgress), percent(difference)));
        } finally {
            // 无论如何都写入缓存，同步更改
            clientTask.setLastReportUploaded(peer.getUploaded());
            clientTask.setLastReportProgress(peer.getProgress());
            progressRecorder.put(peerIpString, lastRecordedProgress);
        }
    }

    private String percent(double d) {
        return (d * 100) + "%";
    }

    record ClientTaskRecord(String address, List<ClientTask> task) {
    }

    @AllArgsConstructor
    @Data
    static class ClientTask {
        private String torrentId;
        private Double lastReportProgress;
        private long lastReportUploaded;
        private long trackingUploadedIncreaseTotal;
    }
}


