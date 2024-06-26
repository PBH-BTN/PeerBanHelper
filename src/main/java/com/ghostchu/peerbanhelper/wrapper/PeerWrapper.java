package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.peer.Peer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeerWrapper {
    private PeerAddressWrapper address;
    private String id;
    private String clientName;
    private long downloaded;
    private long downloadSpeed;
    private long uploaded;
    private long uploadSpeed;
    private double progress;
    private String flags;

    public PeerWrapper(@NotNull Peer peer) {
        this.id = peer.getPeerId();
        this.address = new PeerAddressWrapper(peer.getPeerAddress());
        this.clientName = peer.getClientName();
        this.downloaded = peer.getDownloaded();
        this.downloadSpeed = peer.getDownloadSpeed();
        this.uploaded = peer.getUploaded();
        this.uploadSpeed = peer.getUploadSpeed();
        this.progress = peer.getProgress();
        this.flags = peer.getFlags();
    }
}
