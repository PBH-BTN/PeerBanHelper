package com.ghostchu.peerbanhelper.btn.ping;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PeerConnection {
    private TorrentInfo torrentInfo;
    private PeerInfo peerInfo;
}
