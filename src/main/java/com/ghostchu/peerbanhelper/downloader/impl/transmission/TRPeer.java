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

    /**
     * Retrieves the peer flags from the backend and creates a new PeerFlag object.
     *
     * @return A PeerFlag object representing the peer's current flags, initialized with the flag string from the backend
     */
    @Override
    public PeerFlag getFlags() {
        return new PeerFlag(backend.getFlagStr());
    }

    /**
     * Determines whether the peer is currently in a handshaking state.
     *
     * A peer is considered to be in a handshaking state when both its download
     * and upload speeds are zero or negative, indicating that no data transfer
     * is currently occurring.
     *
     * @return {@code true} if the peer is in a handshaking state (no active data transfer),
     *         {@code false} otherwise
     */
    @Override
    public boolean isHandshaking() {
        return getDownloadSpeed() <= 0 && getUploadSpeed() <= 0;
    }

    /**
     * Returns an empty list of supported peer messages.
     *
     * This method indicates that no specific peer messages are supported by this peer implementation.
     * Always returns an immutable empty list to signify no message support.
     *
     * @return An empty list of {@code PeerMessage} objects
     */
    @Override
    public List<PeerMessage> getSupportedMessages() {
        return Collections.emptyList();
    }

    @Override
    public String getRawIp() {
        return backend.getAddress();
    }

}
