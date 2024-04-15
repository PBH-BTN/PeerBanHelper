package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import lombok.Data;

import java.util.UUID;

@Data
public class BanMetadata implements Comparable<BanMetadata> {
    private String context;
    private UUID randomId ;
    private long banAt;
    private long unbanAt;
    private TorrentWrapper torrent;
    private PeerWrapper peer;
    private String description;
    private String reverseLookup = "N/A";

    public BanMetadata(String context, long banAt, long unbanAt, Torrent torrent, Peer peer, String description) {
        this.randomId = UUID.randomUUID();
        this.context = context;
        this.banAt = banAt;
        this.unbanAt = unbanAt;
        this.torrent = new TorrentWrapper(torrent);
        this.peer = new PeerWrapper(peer);
        this.description = description;
    }

    public BanMetadata() {
    }

    @Override
    public int compareTo(BanMetadata o) {
        return this.randomId.compareTo(o.randomId);
    }

    @Data
    public static class TorrentWrapper {
        private final String id;
        private final long size;
        private final String name;
        private final String hash;

        public TorrentWrapper(Torrent torrent) {
            this.id = torrent.getId();
            this.size = torrent.getSize();
            this.name = torrent.getName();
            this.hash = torrent.getHash();
        }
    }

    @Data
    public static class PeerWrapper {
        private final PeerAddressWrapper address;
        private final String id;
        private final String clientName;
        private final long downloaded;
        private final long uploaded;
        private final double progress;

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
    public static class PeerAddressWrapper {
        private final int port;
        private final String ip;

        public PeerAddressWrapper(PeerAddress address) {
            this.ip = address.getIp();
            this.port = address.getPort();
        }
    }
}
