package com.ghostchu.peerbanhelper.downloader.impl.deluge;

import com.ghostchu.peerbanhelper.downloader.PeerFlag;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import lombok.AllArgsConstructor;
import lombok.Data;

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
}
