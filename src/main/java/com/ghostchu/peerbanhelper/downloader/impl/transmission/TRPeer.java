package com.ghostchu.peerbanhelper.downloader.impl.transmission;

import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.peer.PeerFlag;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import cordelia.rpc.types.Peers;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public final class TRPeer implements Peer {

    private final Peers backend;
    private final transient PeerAddress peerAddress;

    public TRPeer(Peers backend, Function<PeerAddress, PeerAddress> natConverter) {
        this.backend = backend;
        this.peerAddress = natConverter.apply(new PeerAddress(backend.getAddress(), backend.getPort(), backend.getAddress()));
    }

    @Override
    public @NotNull PeerAddress getPeerAddress() {
        return this.peerAddress;
    }

    @Override
    public String getPeerId() {
        return backend.getPeer_id() == null ? "" : backend.getPeer_id();
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
        return backend.getBytes_to_client();
    }

    @Override
    public long getUploadSpeed() {
        return backend.getRateToPeer() == null ? -1 : backend.getRateToPeer();
    }

    @Override
    public long getUploaded() {
        return backend.getBytes_to_peer() == null ? -1 : backend.getBytes_to_peer();
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

}
