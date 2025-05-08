package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.DownloaderBasicInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PeerMetadata {
    private DownloaderBasicInfo downloader;
    private TorrentWrapper torrent;
    private PeerWrapper peer;
    private String reverseLookup = "N/A";

    public PeerMetadata(DownloaderBasicInfo downloader, Torrent torrent, Peer peer) {
        this.downloader = downloader;
        this.torrent = new com.ghostchu.peerbanhelper.wrapper.TorrentWrapper(torrent);
        this.peer = new com.ghostchu.peerbanhelper.wrapper.PeerWrapper(peer);
    }

    public PeerMetadata(DownloaderBasicInfo downloader, TorrentWrapper torrent, PeerWrapper peer) {
        this.downloader = downloader;
        this.torrent = torrent;
        this.peer = peer;
    }

}
