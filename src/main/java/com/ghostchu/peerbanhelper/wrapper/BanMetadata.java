package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BanMetadata implements Comparable<BanMetadata> {
    private String context;
    private UUID randomId;
    private long banAt;
    private long unbanAt;
    private TorrentWrapper torrent;
    private PeerWrapper peer;
    private String rule;
    private String description;
    private String reverseLookup = "N/A";

    public BanMetadata(String context, long banAt, long unbanAt, Torrent torrent, Peer peer, String rule, String description) {
        this.randomId = UUID.randomUUID();
        this.context = context;
        this.banAt = banAt;
        this.unbanAt = unbanAt;
        this.torrent = new TorrentWrapper(torrent);
        this.peer = new PeerWrapper(peer);
        this.rule = rule;
        this.description = description;
    }

    @Override
    public int compareTo(BanMetadata o) {
        return this.randomId.compareTo(o.randomId);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TorrentWrapper {
        private String id;
        private long size;
        private String name;
        private String hash;

        public TorrentWrapper(Torrent torrent) {
            this.id = torrent.getId();
            this.size = torrent.getSize();
            this.name = torrent.getName();
            this.hash = torrent.getHash();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeerWrapper {
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeerAddressWrapper {
        private int port;
        private String ip;

        public PeerAddressWrapper(PeerAddress address) {
            this.ip = address.getIp();
            this.port = address.getPort();
        }
    }
}
