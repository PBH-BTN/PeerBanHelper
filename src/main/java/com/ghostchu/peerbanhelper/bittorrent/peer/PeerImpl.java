package com.ghostchu.peerbanhelper.bittorrent.peer;

import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

@Setter
public final class PeerImpl implements Peer {
    private PeerAddress peerAddress;
    private String rawIp;
    private byte[] peerId;
    private String clientName;
    private long downloadSpeed;
    private long downloaded;
    private long uploadSpeed;
    private long uploaded;
    private double progress;
    private PeerFlag flags;
    private boolean handshaking;

    public PeerImpl(PeerAddress peerAddress, String rawIp, byte[] peerId, String clientName, long downloadSpeed, long downloaded, long uploadSpeed, long uploaded, double progress, PeerFlag flags, boolean handshaking) {
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
        this.handshaking = handshaking;
    }

    @Override
    public  @NotNull PeerAddress getPeerAddress() {
        return peerAddress;
    }

    @Override
    public String getPeerId() {
        return new String(peerId, StandardCharsets.ISO_8859_1);
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

    @Override
    public PeerFlag getFlags() {
        return flags;
    }

    @Override
    public boolean isHandshaking() {
        return handshaking;
    }

    @Override
    public @NotNull String getRawIp() {
        return rawIp;
    }

    public void setPeerAddress(@NotNull PeerAddress peerAddress) {
        this.peerAddress = peerAddress;
    }
}
