package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PeerMetadata {
    private String uniqueId;
    private TorrentWrapper torrent;
    private PeerWrapper peer;
    private String reverseLookup = "N/A";

    public PeerMetadata(String uniqueId, Torrent torrent, Peer peer) {
        this.uniqueId = uniqueId;
        this.torrent = new com.ghostchu.peerbanhelper.wrapper.TorrentWrapper(torrent);
        this.peer = new com.ghostchu.peerbanhelper.wrapper.PeerWrapper(peer);
    }

    public PeerMetadata(String uniqueId, TorrentWrapper torrent, PeerWrapper peer) {
        this.uniqueId = uniqueId;
        this.torrent = torrent;
        this.peer = peer;
    }

}
