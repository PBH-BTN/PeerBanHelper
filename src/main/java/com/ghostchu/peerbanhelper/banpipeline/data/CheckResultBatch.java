package com.ghostchu.peerbanhelper.banpipeline.data;

import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.CheckResult;

public record CheckResultBatch(
        Downloader downloader,
        Torrent torrent,
        Peer peer,
        CheckResult checkResult

) {

}
