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
        long uploaded = peer.getUploaded();
        long torrentSize = torrent.getSize();

        double actualProgress = (double) uploaded / torrentSize;
        double clientProgress = peer.getProgress();

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
