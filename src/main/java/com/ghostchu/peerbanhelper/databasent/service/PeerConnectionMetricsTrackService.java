package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.databasent.table.PeerConnectionMetricsTrackEntity;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutionException;

public interface PeerConnectionMetricsTrackService extends IService<PeerConnectionMetricsTrackEntity> {

    void flushAll();

    int deleteEntries(@NotNull List<PeerConnectionMetricsTrackEntity> entities);

    void syncPeers(@NotNull Downloader downloader, @NotNull Torrent torrent, @NotNull List<Peer> peers) throws ExecutionException;

}
