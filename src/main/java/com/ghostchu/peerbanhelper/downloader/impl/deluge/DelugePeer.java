package com.ghostchu.peerbanhelper.downloader.impl.deluge;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.peer.PeerFlag;
import com.ghostchu.peerbanhelper.peer.PeerMessage;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
@AllArgsConstructor
public final class DelugePeer implements Peer {
    private PeerAddress peerAddress;
    private String peerId;
    private String clientName;
    private long downloaded;
    private long downloadSpeed;
    private long uploaded;
    private long uploadSpeed;
    private double progress;
    private PeerFlag flags;

    /**
     * Retrieves the raw IP address of the peer.
     *
     * @return the IP address of the peer as a string, obtained from the peer's address
     */
    @Override
    public String getRawIp() {
        return peerAddress.getIp();
    }

    /**
     * Determines if the peer is currently in a handshaking state.
     *
     * A peer is considered to be in a handshaking state when both its download and upload speeds
     * are zero or negative, typically indicating an initial connection or negotiation phase.
     *
     * @return {@code true} if the peer is handshaking (no active data transfer),
     *         {@code false} otherwise
     */
    @Override
    public boolean isHandshaking() {
        return downloadSpeed <= 0 && uploadSpeed <= 0;
    }

    /**
     * Returns an empty list of supported peer messages.
     *
     * @return An empty list indicating no specific peer messages are supported by this Deluge peer.
     */
    @Override
    public List<PeerMessage> getSupportedMessages() {
        return Collections.emptyList();
    }
}
