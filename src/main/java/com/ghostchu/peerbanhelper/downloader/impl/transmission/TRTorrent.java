package com.ghostchu.peerbanhelper.downloader.impl.transmission;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import cordelia.rpc.types.Torrents;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public final class TRTorrent implements Torrent {
    private final Torrents backend;

    public TRTorrent(Torrents backend) {
        this.backend = backend;
    }

    @Override
    public String getId() {
        return String.valueOf(backend.getId());
    }

    @Override
    public String getName() {
        return backend.getName();
    }

    @Override
    public String getHash() {
        return backend.getHashString();
    }

    @Override
    public double getProgress() {
        return backend.getPercentDone();
    }

    @Override
    public long getSize() {
        return backend.getTotalSize();
    }

    @Override
    public long getRtUploadSpeed() {
        return backend.getRateUpload();
    }

    @Override
    public long getRtDownloadSpeed() {
        return backend.getRateDownload();
    }

    @NotNull
    public List<Peer> getPeers() {
        return backend.getPeers().stream().map(TRPeer::new).collect(Collectors.toList());
    }

    public Integer getPeerLimit() {
        return backend.getPeerLimit();
    }
}
