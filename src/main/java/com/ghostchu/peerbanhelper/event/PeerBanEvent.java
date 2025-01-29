package com.ghostchu.peerbanhelper.event;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public final class PeerBanEvent {
    private PeerAddress peer;
    private BanMetadata banMetadata;
    private Torrent torrentObj;
    private Peer peerObj;
}
