package com.ghostchu.peerbanhelper.peer;

import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Setter
public class PeerImpl implements Peer {
    @NotNull
    private PeerAddress peerAddress;
    @Nullable
    private String peerId;
    @Nullable
    private String clientName;
    private long downloadSpeed;
    private long downloaded;
    private long uploadSpeed;
    private long uploaded;
    private double progress;
    @Nullable
    private String flags;

    public PeerImpl(@NotNull PeerAddress peerAddress, @Nullable String peerId, @Nullable String clientName,
                    long downloadSpeed, long downloaded, long uploadSpeed,
                    long uploaded, double progress, @Nullable String flags) {
        this.peerAddress = peerAddress;
        this.peerId = peerId;
        this.clientName = clientName;
        this.downloadSpeed = downloadSpeed;
        this.downloaded = downloaded;
        this.uploadSpeed = uploadSpeed;
        this.uploaded = uploaded;
        this.progress = progress;
        this.flags = flags;
    }

    @Override
    public @NotNull PeerAddress getPeerAddress() {
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

    @Override
    public String getFlags() {
        return flags;
    }
}
