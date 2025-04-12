package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PeerMetadata {
    private String downloader;
    private TorrentWrapper torrent;
    private PeerWrapper peer;
    private IPGeoData geo;
    private String reverseLookup = "N/A";

    public PeerMetadata(String downloader, Torrent torrent, Peer peer) {
        this.downloader = downloader;
        this.torrent = new com.ghostchu.peerbanhelper.wrapper.TorrentWrapper(torrent);
        this.peer = new com.ghostchu.peerbanhelper.wrapper.PeerWrapper(peer);
    }

    public PeerMetadata(String downloader, TorrentWrapper torrent, PeerWrapper peer) {
        this.downloader = downloader;
        this.torrent = torrent;
        this.peer = peer;
    }

}
