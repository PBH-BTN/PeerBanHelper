package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.databasent.table.tmp.TrackedSwarmEntity;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;

public interface TrackedSwarmService extends IService<TrackedSwarmEntity> {
    @NotNull Page<TrackedSwarmEntity> getPendingSubmitTrackedPeers(@NotNull Pageable pageable, long idAfterThan);

    void syncPeers(@NotNull Downloader downloader, @NotNull Torrent torrent, @NotNull Peer peer) throws ExecutionException;

    void flushAll();
}
