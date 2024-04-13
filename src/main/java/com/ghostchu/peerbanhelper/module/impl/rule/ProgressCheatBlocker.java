package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class ProgressCheatBlocker extends AbstractFeatureModule {
    private Cache<String, List<ClientTask>> progressRecorder;
    private long torrentMinimumSize;
    private boolean blockExcessiveClients;
    private double excessiveThreshold;
    private double maximumDifference;
    private double rewindMaximumDifference;

    public ProgressCheatBlocker(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public @NotNull String getName() {
        return "Progress Cheat Blocker";
    }

    @Override
    public @NotNull String getConfigName() {
        return "progress-cheat-blocker";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void onEnable() {
        reloadConfig();
    }

    @Override
    public void onDisable() {

    }

    private void reloadConfig() {
        this.progressRecorder = CacheBuilder.newBuilder()
                .maximumSize(512)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();
        this.torrentMinimumSize = getConfig().getLong("minimum-size");
        this.blockExcessiveClients = getConfig().getBoolean("block-excessive-clients");
        this.excessiveThreshold = getConfig().getDouble("excessive-threshold");
        this.maximumDifference = getConfig().getDouble("maximum-difference");
        this.rewindMaximumDifference = getConfig().getDouble("rewind-maximum-difference");
    }

    @Override
    public @NotNull BanResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        final long uploaded = peer.getUploaded();
        final long torrentSize = torrent.getSize();
        if (torrentSize <= 0) {
            return new BanResult(this, PeerAction.NO_ACTION, Lang.MODULE_PCB_SKIP_UNKNOWN_SIZE_TORRENT);
        }
        if (torrentSize < torrentMinimumSize) {
            return new BanResult(this, PeerAction.NO_ACTION, "Skip due the torrent size");
        }
        final double actualProgress = (double) uploaded / torrentSize;
        final double clientProgress = peer.getProgress();
        // uploaded = -1 代表客户端不支持统计此 Peer 总上传量
        if (uploaded != -1 && blockExcessiveClients && (uploaded > torrentSize)) {
            // 下载过量，检查
            long maxAllowedExcessiveThreshold = (long) (torrentSize * excessiveThreshold);
            if (uploaded > maxAllowedExcessiveThreshold) {
                return new BanResult(this, PeerAction.BAN, String.format(Lang.MODULE_PCB_EXCESSIVE_DOWNLOAD, torrentSize, uploaded, maxAllowedExcessiveThreshold));
            }
        }

        if (actualProgress - clientProgress <= 0) {
            return new BanResult(this, PeerAction.NO_ACTION, String.format(Lang.MODULE_PCB_PEER_MORE_THAN_LOCAL_SKIP, clientProgress, actualProgress));
        }

        double difference = Math.abs(actualProgress - clientProgress);
        if (difference > maximumDifference) {
            return new BanResult(this, PeerAction.BAN, String.format(Lang.MODULE_PCB_PEER_BAN_INCORRECT_PROGRESS, clientProgress, actualProgress, difference));
        }

        double rewindAllow = rewindMaximumDifference;
        if (rewindAllow > 0) {
            List<ClientTask> lastRecordedProgress = progressRecorder.getIfPresent(peer.getAddress().getIp());
            if (lastRecordedProgress == null) lastRecordedProgress = new ArrayList<>();
            ClientTask clientTask = new ClientTask(torrent.getId(), 0d);
            for (ClientTask recordedProgress : lastRecordedProgress) {
                if (recordedProgress.getTorrentId().equals(torrent.getId())) {
                    clientTask = recordedProgress;
                    break;
                }
            }
            double lastRecord = clientTask.getProgress();
            clientTask.setProgress(clientProgress);
            progressRecorder.put(peer.getAddress().getIp(), lastRecordedProgress);
            double rewind = lastRecord - peer.getProgress();
            boolean ban = rewind > rewindAllow;
            return new BanResult(this, ban ? PeerAction.BAN : PeerAction.NO_ACTION, String.format(Lang.MODULE_PCB_PEER_BAN_REWIND, clientProgress, actualProgress, lastRecord, rewind, rewindAllow));
        }
        return new BanResult(this, PeerAction.NO_ACTION, String.format(Lang.MODULE_PCB_PEER_BAN_INCORRECT_PROGRESS, percent(clientProgress), percent(actualProgress), percent(difference)));
    }

    private String percent(double d){
        return (d*100)+"%";
    }


    @AllArgsConstructor
    @Data
    static class ClientTask {
        private String torrentId;
        private Double progress;
    }
}


