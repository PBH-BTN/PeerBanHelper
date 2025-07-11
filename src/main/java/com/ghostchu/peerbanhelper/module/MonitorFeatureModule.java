package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface MonitorFeatureModule extends FeatureModule {
    void onTorrentPeersRetrieved(@NotNull Downloader downloader, @NotNull Torrent torrent, @NotNull List<Peer> peers);
}
