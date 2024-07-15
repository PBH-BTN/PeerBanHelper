package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PeerMetadata implements Comparable<PeerMetadata> {
    private String downloader;
    private UUID randomId;
    private TorrentWrapper torrent;
    private PeerWrapper peer;
    private IPGeoData geo;
    private String reverseLookup = "N/A";

    public PeerMetadata(String downloader, Torrent torrent, Peer peer) {
        this.randomId = UUID.randomUUID();
        this.downloader = downloader;
        this.torrent = new com.ghostchu.peerbanhelper.wrapper.TorrentWrapper(torrent);
        this.peer = new com.ghostchu.peerbanhelper.wrapper.PeerWrapper(peer);
    }

    public PeerMetadata(String downloader, TorrentWrapper torrent, PeerWrapper peer) {
        this.randomId = UUID.randomUUID();
        this.downloader = downloader;
        this.torrent = torrent;
        this.peer = peer;
    }


    @Override
    public int compareTo(PeerMetadata o) {
        return this.randomId.compareTo(o.randomId);
    }
}
