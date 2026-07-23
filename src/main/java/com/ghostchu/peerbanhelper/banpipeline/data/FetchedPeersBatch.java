package com.ghostchu.peerbanhelper.banpipeline.data;

import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;

import java.util.List;

public record FetchedPeersBatch(
        Downloader downloader,
        Torrent torrent,
        List<? extends Peer> peers
) {

}
