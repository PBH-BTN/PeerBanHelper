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

    @Override
    public String getRawIp() {
        return peerAddress.getIp();
    }

    @Override
    public boolean isHandshaking() {
        return downloadSpeed <= 0 && uploadSpeed <= 0;
    }

    @Override
    public List<PeerMessage> getSupportedMessages() {
        return Collections.emptyList();
    }
}
