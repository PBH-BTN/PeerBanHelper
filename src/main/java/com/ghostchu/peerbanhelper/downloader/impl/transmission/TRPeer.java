package com.ghostchu.peerbanhelper.downloader.impl.transmission;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import cordelia.rpc.types.Peers;

public class TRPeer implements Peer {

    private final Peers backend;

    public TRPeer(Peers backend){
        this.backend = backend;
    }
    @Override
    public PeerAddress getAddress() {
        return new PeerAddress(backend.getAddress(), backend.getPort());
    }

    @Override
    public String getPeerId() {
        return "<Unsupported>";
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
    public long getUploadedSpeed() {
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

}
