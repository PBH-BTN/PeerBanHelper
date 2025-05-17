package com.ghostchu.peerbanhelper.api.module;

import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.api.downloader.Downloader;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ExecutorService;

public interface MonitorFeatureModule extends FeatureModule {
    void onTorrentPeersRetrieved(@NotNull Downloader downloader, @NotNull Torrent torrent, @NotNull List<Peer> peers, @NotNull ExecutorService ruleExecuteExecutor);
}
