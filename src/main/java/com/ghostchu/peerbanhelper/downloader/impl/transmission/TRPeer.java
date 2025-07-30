package com.ghostchu.peerbanhelper.downloader.impl.transmission;

import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.peer.PeerFlag;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import cordelia.rpc.types.Peers;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public final class TRPeer implements Peer {

    private final Peers backend;
    private transient PeerAddress peerAddress;

    public TRPeer(Peers backend, Function<PeerAddress, PeerAddress> natConverter) {
        this.backend = backend;
        this.peerAddress = natConverter.apply(new PeerAddress(backend.getAddress(), backend.getPort()));
    }

    @Override
    public @NotNull PeerAddress getPeerAddress() {
        return this.peerAddress;
    }

    @Override
    public String getPeerId() {
        return "";
    }

    @Override
    public String getClientName() {
        return backend.getClientName();
    }

    @Override
    public long getDownloadSpeed() {
        return backend.getRateToClient();
    }

    @Override
    public long getDownloaded() {
        return -1; // Unsupported
    }

    @Override
    public long getUploadSpeed() {
        return backend.getRateToPeer();
    }

    @Override
    public long getUploaded() {
        return -1; // Unsupported
    }

    @Override
    public double getProgress() {
        return backend.getProgress();
    }

    @Override
    public PeerFlag getFlags() {
        return new PeerFlag(backend.getFlagStr());
    }

    @Override
    public boolean isHandshaking() {
        return getDownloadSpeed() <= 0 && getUploadSpeed() <= 0;
    }

    @Override
    public @NotNull String getRawIp() {
        return backend.getAddress();
    }

}
