package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;

public interface Runner {
    @NotNull
    RunnerAction shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader);
}
