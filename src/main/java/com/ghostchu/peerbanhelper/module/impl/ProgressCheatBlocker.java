package com.ghostchu.peerbanhelper.module.impl;

import com.ghostchu.peerbanhelper.config.ModuleBaseConfigSection;
import com.ghostchu.peerbanhelper.config.section.ModuleProgressCheatBlockerConfigSection;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class ProgressCheatBlocker extends AbstractFeatureModule<ModuleProgressCheatBlockerConfigSection> {

    private Cache<String, List<ClientTask>> progressRecorder = CacheBuilder.newBuilder()
            .maximumSize(512)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

    public ProgressCheatBlocker(ModuleBaseConfigSection section) {
        super(section);
    }

    @Override
    public String getName() {
        return "Progress Cheat Blocker";
    }

    @Override
    public BanResult shouldBanPeer(Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        final long uploaded = peer.getUploaded();
        final long torrentSize = torrent.getSize();
        if (torrentSize <= 0) {
            return new BanResult(this,PeerAction.NO_ACTION, Lang.MODULE_PCB_SKIP_UNKNOWN_SIZE_TORRENT);
        }
        if (torrentSize < getConfig().getMinimumSize()) {
            return new BanResult(this,PeerAction.NO_ACTION, "Skip due the torrent size");
        }
        final double actualProgress = (double) uploaded / torrentSize;
        final double clientProgress = peer.getProgress();
        // uploaded = -1 代表客户端不支持统计此 Peer 总上传量
        if (uploaded != -1 && getConfig().isBlockExcessiveClients() && (uploaded > torrentSize)) {
            // 下载过量，检查
            long maxAllowedExcessiveThreshold = (long) (torrentSize * getConfig().getExcessiveThreshold());
            if (uploaded > maxAllowedExcessiveThreshold) {
                return new BanResult(this,PeerAction.BAN, String.format(Lang.MODULE_PCB_EXCESSIVE_DOWNLOAD, torrentSize, uploaded, maxAllowedExcessiveThreshold));
            }
        }

        if (actualProgress - clientProgress <= 0) {
            return new BanResult(this,PeerAction.NO_ACTION, String.format(Lang.MODULE_PCB_PEER_MORE_THAN_LOCAL_SKIP, clientProgress, actualProgress));
        }

        double difference = Math.abs(actualProgress - clientProgress);
        if (difference > getConfig().getMaximumDifference()) {
            return new BanResult(this,PeerAction.BAN, String.format(Lang.MODULE_PCB_PEER_BAN_INCORRECT_PROGRESS, clientProgress, actualProgress, difference));
        }

        double rewindAllow = getConfig().getRewindMaximumDifference();
        if (rewindAllow > 0) {
            List<ClientTask> lastRecordedProgress = progressRecorder.getIfPresent(peer.getAddress());
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
            return new BanResult(this,ban ? PeerAction.BAN : PeerAction.NO_ACTION, String.format(Lang.MODULE_PCB_PEER_BAN_REWIND, clientProgress, actualProgress, lastRecord, rewind, rewindAllow));
        }
        return new BanResult(this,PeerAction.NO_ACTION, String.format(Lang.MODULE_PCB_PEER_BAN_INCORRECT_PROGRESS, clientProgress, actualProgress, difference));
    }

    static class ClientTask {
        private String torrentId;
        private Double progress;

        public ClientTask(String torrentId, Double progress) {
            this.torrentId = torrentId;
            this.progress = progress;
        }

        public String getTorrentId() {
            return this.torrentId;
        }

        public void setTorrentId(String torrentId) {
            this.torrentId = torrentId;
        }

        public Double getProgress() {
            return this.progress;
        }

        public void setProgress(Double progress) {
            this.progress = progress;
        }
    }
}
