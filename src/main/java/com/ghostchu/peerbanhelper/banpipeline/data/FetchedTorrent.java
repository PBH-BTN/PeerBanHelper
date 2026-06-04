package com.ghostchu.peerbanhelper.banpipeline.data;

import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;

public record FetchedTorrent(
        Downloader downloader,
        Torrent torrent
) {

}
