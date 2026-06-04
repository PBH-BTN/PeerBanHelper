package com.ghostchu.peerbanhelper.banpipeline.data;

import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;

import java.util.List;

public record FetchedTorrentsBatch(
        Downloader downloader,
        List<Torrent> torrent
) {

}
