package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public interface BatchMonitorFeatureModule extends FeatureModule {
    void onPeersRetrieved(@NotNull Map<Downloader, Map<Torrent, List<Peer>>> peers);
}
