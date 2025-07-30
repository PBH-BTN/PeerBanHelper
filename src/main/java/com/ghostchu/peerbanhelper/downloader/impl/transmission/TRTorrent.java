package com.ghostchu.peerbanhelper.downloader.impl.transmission;

import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.bittorrent.tracker.Tracker;
import com.ghostchu.peerbanhelper.bittorrent.tracker.TrackerImpl;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import cordelia.rpc.types.Torrents;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class TRTorrent implements Torrent {
    private final Torrents backend;
    private final Function<PeerAddress, PeerAddress> natConverter;

    public TRTorrent(Torrents backend, Function<PeerAddress, PeerAddress> natConverter) {
        this.backend = backend;
        this.natConverter = natConverter;
    }

    @Override
    public  @NotNull String getId() {
        return String.valueOf(backend.getId());
    }

    @Override
    public  @NotNull String getName() {
        return backend.getName();
    }

    @Override
    public  @NotNull String getHash() {
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
    public long getCompletedSize() {
        return (long) (backend.getSizeWhenDone() * backend.getPercentDone());
    }

    @Override
    public long getRtUploadSpeed() {
        return backend.getRateUpload();
    }

    @Override
    public long getRtDownloadSpeed() {
        return backend.getRateDownload();
    }

    @Override
    public boolean isPrivate() {
        return backend.getIsPrivate();
    }

    @NotNull
    public List<Peer> getPeers() {
        return backend.getPeers().stream().map(backend -> new TRPeer(backend, natConverter)).collect(Collectors.toList());
    }

    public Integer getPeerLimit() {
        return backend.getPeerLimit();
    }

    public List<Tracker> getTrackers() {
        return TrackerImpl.parseFromTrackerList(backend.getTrackerList());
    }
}
