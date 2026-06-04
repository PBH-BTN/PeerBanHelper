package com.ghostchu.peerbanhelper.banpipeline.data;

import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.FeatureModule;

import java.util.Map;

public record CheckedPeersBatch(
        Downloader downloader,
        Torrent torrent,
        FeatureModule module,
        Map<Peer, CheckResult> peers
) {

}
