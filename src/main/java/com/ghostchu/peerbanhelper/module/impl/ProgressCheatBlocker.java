package com.ghostchu.peerbanhelper.module.impl;

import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.util.concurrent.TimeUnit;

public class ProgressCheatBlocker extends AbstractFeatureModule {
    private Cache<PeerAddress, Double> progressRecorder = CacheBuilder.newBuilder()
            .maximumSize(10240)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

    public ProgressCheatBlocker(YamlConfiguration profile) {
        super(profile);
    }

    @Override
    public String getName() {
        return "Progress Cheat Blocker";
    }

    @Override
    public String getConfigName() {
        return "progress-cheat-blocker";
    }

    @Override
    public BanResult shouldBanPeer(Torrent torrent, Peer peer) {
        final long uploaded = peer.getUploaded();
        final long torrentSize = torrent.getSize();

        final double actualProgress = (double) uploaded / torrentSize;
        final double clientProgress = peer.getProgress();

        if(getConfig().getBoolean("block-excessive-clients") && (uploaded > torrentSize)){
            // 下载过量，检查
            long maxAllowedExcessiveThreshold = (long) (torrentSize * getConfig().getDouble("excessive-threshold"));
            if(uploaded > maxAllowedExcessiveThreshold){
                return new BanResult(true, "客户端下载过量：种子大小："+torrentSize+"，上传给此对等体的总量："+uploaded+"，最大允许的过量下载总量："+maxAllowedExcessiveThreshold);
            }
        }

        if (actualProgress - clientProgress <= 0) {
            return new BanResult(false, "客户端进度：" + formatPercent(clientProgress) + "，实际进度：" + formatPercent(actualProgress) + "，客户端的进度多于本地进度，跳过检测");
        }

        double difference = Math.abs(actualProgress - clientProgress);
        if (difference > getConfig().getDouble("maximum-difference")) {
            return new BanResult(true, "客户端进度：" + formatPercent(clientProgress) + "，实际进度：" + formatPercent(actualProgress) + "，差值：" + formatPercent(difference));
        }

        double rewindAllow = getConfig().getDouble("rewind-maximum-difference");
        if (rewindAllow > 0) {
            Double lastRecordedProgress = progressRecorder.getIfPresent(peer.getAddress());
            if (lastRecordedProgress != null) {
                double rewind = lastRecordedProgress - peer.getProgress();
                boolean ban = rewind > rewindAllow;
                return new BanResult(ban, "客户端进度：" + formatPercent(clientProgress) + "，实际进度：" + formatPercent(actualProgress) + "，上次记录进度：" + formatPercent(lastRecordedProgress) + "，本次进度：" + formatPercent(rewind) + "，差值：" + formatPercent(rewindAllow));
            }
        }
        return new BanResult(false, "客户端进度：" + formatPercent(clientProgress) + "，实际进度：" + formatPercent(actualProgress) + "，差值：" + formatPercent(difference));
    }

    private String formatPercent(double d) {
        return String.format("%.2f", d * 100) + "%";
    }
}
