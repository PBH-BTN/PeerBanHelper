package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.net.HostAndPort;
import inet.ipaddr.IPAddress;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public final class IdleConnectionDosProtection extends AbstractRuleFeatureModule implements Reloadable {
    private final Map<HostAndPort, ConnectionInfo> idleConnections = new ConcurrentHashMap<>();
    private long banDuration;
    private long maxAllowedIdleTime;
    private long idleSpeedThreshold;
    private boolean resetOnStatusChange;
    private double minStatusChangePercentage;

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
    }

    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader) {
        HostAndPort hostAndPort = HostAndPort.fromParts(peer.getPeerAddress().getIp(), peer.getPeerAddress().getPort());
        // 如果速度够快，那么认为不是空闲连接
        if (peer.getUploadSpeed() > idleSpeedThreshold || peer.getDownloadSpeed() > idleSpeedThreshold) {
            log.debug("Peer {} speed is above threshold, not idle", hostAndPort);
            idleConnections.remove(hostAndPort);
            return pass();
        }
        // 如果速度不够快，则从 map 里获取连接信息
        ConnectionInfo info = idleConnections.getOrDefault(hostAndPort, new ConnectionInfo(System.currentTimeMillis(), peer.getProgress(), peer.getUploaded(), peer.getDownloaded()));
        long computedAvgUploadSpeed = (peer.getUploaded() - info.getUploaded()) / (System.currentTimeMillis() - info.getIdleStartTime() + 1);
        long computedAvgDownloadSpeed = (peer.getDownloaded() - info.getDownloaded()) / (System.currentTimeMillis() - info.getIdleStartTime() + 1);
        double percentageChange = Math.abs(peer.getProgress() - info.getPercentage());
        if (computedAvgUploadSpeed > idleSpeedThreshold || computedAvgDownloadSpeed > idleSpeedThreshold) {
            log.debug("Peer {} computed avg speed is above threshold, not idle", hostAndPort);
            idleConnections.remove(hostAndPort);
            return pass();
        }
        if (resetOnStatusChange && percentageChange >= minStatusChangePercentage) {
            log.debug("Peer {} status changed by {}% in computing window, resetting idle timer", hostAndPort, percentageChange);
            idleConnections.remove(hostAndPort);
            return pass();
        }
        long alreadyIdled = System.currentTimeMillis() - info.getIdleStartTime();
        // 如果已经空闲时间超过最大允许时间，则封禁
        if (alreadyIdled > maxAllowedIdleTime) {
            log.debug("Peer {} idle timed out", hostAndPort);
            idleConnections.remove(hostAndPort);
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
        } else {
            log.debug("Updating idle info for peer {}, already idled {} ms, percentage change {}%, uploaded {}, downloaded {}", hostAndPort, alreadyIdled, percentageChange, peer.getUploaded(), peer.getDownloaded());
            idleConnections.put(hostAndPort, info);
        }
        return pass();
    }

    @Data
    @AllArgsConstructor
    static class ConnectionInfo {
        private long idleStartTime;
        private double percentage;
        private long uploaded;
        private long downloaded;
    }
}
