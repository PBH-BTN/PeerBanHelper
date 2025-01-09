package com.ghostchu.peerbanhelper.downloader.impl.transmission;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.peer.PeerFlag;
import com.ghostchu.peerbanhelper.peer.PeerMessage;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import cordelia.rpc.types.Peers;

import java.util.Collections;
import java.util.List;

public final class TRPeer implements Peer {

    private final Peers backend;
    private transient PeerAddress peerAddress;

    public TRPeer(Peers backend) {
        this.backend = backend;
    }

    @Override
    public PeerAddress getPeerAddress() {
        if (this.peerAddress == null) {
            this.peerAddress = new PeerAddress(backend.getAddress(), backend.getPort());
        }
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
    public List<PeerMessage> getSupportedMessages() {
        return Collections.emptyList();
    }

    @Override
    public String getRawIp() {
        return backend.getAddress();
    }

}
