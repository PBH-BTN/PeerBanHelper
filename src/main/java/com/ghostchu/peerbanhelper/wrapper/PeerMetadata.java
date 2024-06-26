package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PeerMetadata implements Comparable<PeerMetadata> {
    private String downloader;
    private UUID randomId;
    private TorrentWrapper torrent;
    private PeerWrapper peer;
    private String reverseLookup = "N/A";


    public PeerMetadata(@NotNull String downloader, @NotNull Torrent torrent, @NotNull Peer peer) {
        this.randomId = UUID.randomUUID();
        this.downloader = downloader;
        this.torrent = new com.ghostchu.peerbanhelper.wrapper.TorrentWrapper(torrent);
        this.peer = new com.ghostchu.peerbanhelper.wrapper.PeerWrapper(peer);
    }

    public PeerMetadata(@NotNull String downloader, @NotNull TorrentWrapper torrent, @NotNull PeerWrapper peer) {
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
