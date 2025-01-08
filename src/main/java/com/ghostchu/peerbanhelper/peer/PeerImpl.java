package com.ghostchu.peerbanhelper.peer;

import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import lombok.Setter;

import java.util.List;

@Setter
public final class PeerImpl implements Peer {
    private PeerAddress peerAddress;
    private String rawIp;
    private String peerId;
    private String clientName;
    private long downloadSpeed;
    private long downloaded;
    private long uploadSpeed;
    private long uploaded;
    private double progress;
    private PeerFlag flags;
    private List<PeerMessage> supportedMessages;
    private boolean handshaking;

    /**
     * Constructs a new PeerImpl instance with comprehensive peer information.
     *
     * @param peerAddress The network address of the peer
     * @param rawIp The raw IP address of the peer
     * @param peerId Unique identifier for the peer
     * @param clientName Name of the peer's client software
     * @param downloadSpeed Current download speed in bytes per second
     * @param downloaded Total amount of data downloaded by the peer
     * @param uploadSpeed Current upload speed in bytes per second
     * @param uploaded Total amount of data uploaded by the peer
     * @param progress Current download progress as a percentage (0.0 to 1.0)
     * @param flags Peer-specific flags representing its current state
     * @param supportedMessages List of message types supported by the peer
     * @param handshaking Boolean indicating whether the peer is currently in a handshake state
     */
    public PeerImpl(PeerAddress peerAddress, String rawIp, String peerId, String clientName, long downloadSpeed, long downloaded, long uploadSpeed, long uploaded, double progress, PeerFlag flags, List<PeerMessage> supportedMessages, boolean handshaking) {
        this.peerAddress = peerAddress;
        this.rawIp = rawIp;
        this.peerId = peerId;
        this.clientName = clientName;
        this.downloadSpeed = downloadSpeed;
        this.downloaded = downloaded;
        this.uploadSpeed = uploadSpeed;
        this.uploaded = uploaded;
        this.progress = progress;
        this.flags = flags;
        this.supportedMessages = supportedMessages;
        this.handshaking = handshaking;
    }

    /**
     * Retrieves the network address of the peer.
     *
     * @return the {@code PeerAddress} representing the network location and details of this peer
     */
    @Override
    public PeerAddress getPeerAddress() {
        return peerAddress;
    }

    @Override
    public String getPeerId() {
        return peerId;
    }

    @Override
    public String getClientName() {
        return clientName;
    }

    @Override
    public long getDownloadSpeed() {
        return downloadSpeed;
    }

    @Override
    public long getDownloaded() {
        return downloaded;
    }

    @Override
    public long getUploadSpeed() {
        return uploadSpeed;
    }

    @Override
    public long getUploaded() {
        return uploaded;
    }

    @Override
    public double getProgress() {
        return progress;
    }

    /**
     * Retrieves the flags associated with this peer.
     *
     * @return the {@link PeerFlag} representing the current status or attributes of the peer
     */
    @Override
    public PeerFlag getFlags() {
        return flags;
    }

    /**
     * Checks if the peer is currently in the handshaking process.
     *
     * @return {@code true} if the peer is handshaking, {@code false} otherwise
     */
    @Override
    public boolean isHandshaking() {
        return handshaking;
    }

    /**
     * Retrieves the raw IP address of the peer.
     *
     * @return the IP address of the peer as a {@code String}
     */
    @Override
    public String getRawIp() {
        return rawIp;
    }

    @Override
    public List<PeerMessage> getSupportedMessages() {
        return supportedMessages;
    }
}
