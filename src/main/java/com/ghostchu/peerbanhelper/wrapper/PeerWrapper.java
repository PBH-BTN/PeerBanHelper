package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.peer.Peer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeerWrapper {
    private PeerAddressWrapper address;
    private String id;
    private String clientName;
    private long downloaded;
    private long uploaded;
    private double progress;

    public PeerWrapper(Peer peer) {
        this.id = peer.getPeerId();
        this.address = new PeerAddressWrapper(peer.getAddress());
        this.clientName = peer.getClientName();
        this.downloaded = peer.getDownloaded();
        this.uploaded = peer.getUploaded();
        this.progress = peer.getProgress();
    }
}
