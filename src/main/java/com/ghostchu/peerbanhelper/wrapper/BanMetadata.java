package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BanMetadata implements Comparable<BanMetadata> {

    private String context;
    private String downloader;
    private UUID randomId;
    private long banAt;
    private long unbanAt;
    private TorrentWrapper torrent;
    private PeerWrapper peer;
    private String rule;
    private String description;
    private String reverseLookup = "N/A";
    private GeoWrapper geo;
    private ASNWrapper asn;

    public BanMetadata(String context, String downloader, long banAt, long unbanAt, Torrent torrent, Peer peer, String rule,
                       String description, @Nullable CityResponse cityResponse, @Nullable AsnResponse asnResponse) {
        this.randomId = UUID.randomUUID();
        this.context = context;
        this.downloader = downloader;
        this.banAt = banAt;
        this.unbanAt = unbanAt;
        this.torrent = new TorrentWrapper(torrent);
        this.peer = new PeerWrapper(peer);
        this.rule = rule;
        this.description = description;
        if (cityResponse != null) {
            this.geo = new GeoWrapper(cityResponse);
        }
        if (asnResponse != null) {
            this.asn = new ASNWrapper(asnResponse);
        }
    }

    @Override
    public int compareTo(BanMetadata o) {
        return this.randomId.compareTo(o.randomId);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoWrapper {
        private String iso;
        private String countryRegion;
        private String city;
        private Double latitude;
        private Double longitude;
        private Integer accuracyRadius;

        public GeoWrapper(CityResponse cityResponse) {
            if (cityResponse.getCountry() != null) {
                this.iso = cityResponse.getCountry().getIsoCode();
                this.countryRegion = cityResponse.getCountry().getName();
            }
            if (cityResponse.getCity() != null) {
                this.city = cityResponse.getCity().getName();
            }
            if (cityResponse.getLocation() != null) {
                this.latitude = cityResponse.getLocation().getLatitude();
                this.longitude = cityResponse.getLocation().getLongitude();
                this.accuracyRadius = cityResponse.getLocation().getAccuracyRadius();
            }
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ASNWrapper {
        private long asn;
        private String asOrganization;
        private String asNetwork;

        public ASNWrapper(AsnResponse asnResponse) {
            this.asn = asnResponse.getAutonomousSystemNumber();
            this.asOrganization = asnResponse.getAutonomousSystemOrganization();
            this.asNetwork = asnResponse.getNetwork().toString();
        }
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
