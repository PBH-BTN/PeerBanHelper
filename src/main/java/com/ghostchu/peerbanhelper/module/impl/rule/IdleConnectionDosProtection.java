package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.*;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.net.HostAndPort;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public final class IdleConnectionDosProtection extends AbstractRuleFeatureModule implements Reloadable, BatchMonitorFeatureModule {
    private final Cache<@NotNull HostAndPort, @NotNull ConnectionInfo> idleConnections = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .softValues()
            .build();
    private long banDuration;
    private long maxAllowedIdleTime;
    private long idleSpeedThreshold;
    private boolean resetOnStatusChange;
    private double minStatusChangePercentage;
    private ProtectionMode protectionMode;

    @Override
    public @NotNull String getName() {
        return "Idle Connection DoS Protection";
    }

    @Override
    public @NotNull String getConfigName() {
        return "idle-connection-dos-protection";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void onEnable() {
        reloadConfig();
        Main.getReloadManager().register(this);
    }

    @Override
    public boolean isThreadSafe() {
        return true;
    }

    @Override
    public void onDisable() {
        Main.getReloadManager().unregister(this);
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }

    public void reloadConfig() {
        this.banDuration = getConfig().getLong("ban-duration", 0);
        this.maxAllowedIdleTime = getConfig().getLong("max-allowed-idle-time");
        this.idleSpeedThreshold = getConfig().getLong("idle-speed-threshold");
        this.minStatusChangePercentage = getConfig().getDouble("min-status-change-percentage");
        this.resetOnStatusChange = getConfig().getBoolean("reset-on-status-change");
        this.protectionMode = ProtectionMode.fromCode(getConfig().getInt("protect-mode", 0));
    }

    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader) {
        // 先检查种子状态
        if (protectionMode == ProtectionMode.ALWAYS_SEEDING && !torrent.isSeeding()) {
            return pass();
        }
        var flags = peer.getFlags();
        if (protectionMode == ProtectionMode.DETERMINED_BY_PEER_FLAGS) {
            // 如果是做种种子，那么无论如何都要保护
            if (!torrent.isSeeding()) {
                // 现在是下载种子，检查 Peer Flags 可用性
                if (flags == null) {
                    return pass(); // 不支持 Peer Flags 的下载器不保护下载种子
                }
                // 检查 Peer Flags
                if((flags.isInteresting()    // 存在 d/D，兴趣系统在工作，Peer 应该已发送 BIT_FIELD 或者 Fast Extension 更新了本地分段信息，使得本地兴趣系统活动，视为连接活动
                        || flags.isRemoteInterested() // 存在 u/U，远程对我们感兴趣，我们发送的 BIT_FIELD 或者 Fast Extension 更新了远程兴趣系统并得到响应，视为连接活动
                ) && ExternalSwitch.parseBoolean("pbh.module.idle-connection-dos-protection.ignore-if-any-interested", true)){
                    return pass(); // 这类种子一般连接挺好的，忽略它们
                }
            }
        }
        HostAndPort hostAndPort = HostAndPort.fromParts(peer.getPeerAddress().getIp(), peer.getPeerAddress().getPort());
        // 如果速度够快，那么认为不是空闲连接
        if (peer.getUploadSpeed() > idleSpeedThreshold || peer.getDownloadSpeed() > idleSpeedThreshold) {
            //log.debug("Peer {} speed is above threshold, not idle", hostAndPort);
            idleConnections.invalidate(hostAndPort);
            return pass();
        }
        // 如果速度不够快，则从 map 里获取连接信息
        ConnectionInfo info;
        try {
            info = idleConnections.get(hostAndPort, ()->new ConnectionInfo(System.currentTimeMillis(), peer.getProgress(), peer.getUploaded(), peer.getDownloaded(), 0));
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        long computedAvgUploadSpeed = (peer.getUploaded() - info.getUploaded()) / (System.currentTimeMillis() - info.getIdleStartTime() + 1);
        long computedAvgDownloadSpeed = (peer.getDownloaded() - info.getDownloaded()) / (System.currentTimeMillis() - info.getIdleStartTime() + 1);
        double percentageChange = Math.abs(peer.getProgress() * 100.0d - info.getPercentage());
        if (computedAvgUploadSpeed > idleSpeedThreshold || computedAvgDownloadSpeed > idleSpeedThreshold) {
            //log.debug("Peer {} computed avg speed is above threshold, not idle", hostAndPort);
            idleConnections.invalidate(hostAndPort);
            return pass();
        }
        if (resetOnStatusChange && percentageChange >= minStatusChangePercentage) {
            //log.debug("Peer {} status changed by {}% in computing window, resetting idle timer", hostAndPort, percentageChange);
            idleConnections.invalidate(hostAndPort);
            return pass();
        }
        long alreadyIdled = System.currentTimeMillis() - info.getIdleStartTime();
        // 如果已经空闲时间超过最大允许时间，则封禁
        if (alreadyIdled > maxAllowedIdleTime) {
            //log.debug("Peer {} idle timed out", hostAndPort);
            idleConnections.invalidate(hostAndPort);
            return new CheckResult(getClass(), PeerAction.BAN, banDuration,
                    new TranslationComponent(Lang.MODULE_ICDP_RULE_TITLE),
                    new TranslationComponent(Lang.MODULE_ICDP_RULE_DESCRIPTION, hostAndPort.toString()),
                    new StructuredData<String, Object>()
                            .add("ip", peer.getPeerAddress().toString())
                            .add("idle_type", "timeout")
                            .add("idle_start", info.getIdleStartTime())
                            .add("last_percentage", info.getPercentage())
                            .add("last_uploaded", info.getUploaded())
                            .add("last_downloaded", info.getDownloaded())
                            .add("percentage", peer.getProgress())
                            .add("uploaded", peer.getUploaded())
                            .add("downloaded", peer.getDownloaded())
                            .add("upload_speed", peer.getUploadSpeed())
                            .add("download_speed", peer.getDownloadSpeed())
            );
        }
        //log.debug("Updating idle info for peer {}, already idled {} ms, percentage change {}%, uploaded {}, downloaded {}", hostAndPort, alreadyIdled, percentageChange, peer.getUploaded(), peer.getDownloaded());
        idleConnections.put(hostAndPort, info);
        return pass();
    }

    @Override
    public void onPeersRetrieved(@NotNull Map<Downloader, Map<Torrent, List<Peer>>> peers) {
        var allPeers = peers.values().stream()
                .flatMap(map -> map.values().stream())
                .flatMap(Collection::stream)
                .map(peer -> HostAndPort.fromParts(peer.getPeerAddress().getIp(), peer.getPeerAddress().getPort()))
                .toList();
        long count = idleConnections.size();
        idleConnections.asMap().entrySet().removeIf(entry -> {
            var hostAndPort = entry.getKey();
            if (!allPeers.contains(hostAndPort)) {
                entry.getValue().setNotHitCounter(entry.getValue().getNotHitCounter() + 1);
            }
            //log.debug("Removing idle connection {} due to not being seen for 5 checks", hostAndPort);
            return entry.getValue().getNotHitCounter() > 5;
        });
        //log.debug("Removed {} disconnected connections.", count - idleConnections.size());
    }

    @Data
    @AllArgsConstructor
    static class ConnectionInfo {
        private long idleStartTime;
        private double percentage;
        private long uploaded;
        private long downloaded;
        private int notHitCounter;
    }

    @Getter
    enum ProtectionMode {
        DETERMINED_BY_PEER_FLAGS(0),
        ALWAYS_SEEDING(1),
        ALWAYS_SEEDING_AND_DOWNLOADING(2);

        private final int code;

        ProtectionMode(int code) {
            this.code = code;
        }

        public static ProtectionMode fromCode(int code) {
            for (ProtectionMode mode : values()) {
                if (mode.code == code) {
                    return mode;
                }
            }
            return DETERMINED_BY_PEER_FLAGS;
        }
    }
}
