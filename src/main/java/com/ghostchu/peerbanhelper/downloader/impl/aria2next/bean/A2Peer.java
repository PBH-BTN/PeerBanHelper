package com.ghostchu.peerbanhelper.downloader.impl.aria2next.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.peer.PeerFlag;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@NoArgsConstructor
@Data
public class A2Peer implements Peer {

    @JsonProperty("amChoking")
    private Boolean amChoking;
    @JsonProperty("amInterested")
    private Boolean amInterested;
    @JsonProperty("bitfield")
    private String bitfield;
    @JsonProperty("completedLength")
    private Long completedLength;
    @JsonProperty("downloadSpeed")
    private Long downloadSpeed;
    @JsonProperty("downloaded")
    private Long downloaded;
    @JsonProperty("flags")
    private String flags;
    @JsonProperty("handshaking")
    private Boolean handshaking;
    @JsonProperty("incoming")
    private Boolean incoming;
    @JsonProperty("ip")
    private String ip;
    @JsonProperty("optimisticUnchoke")
    private Boolean optimisticUnchoke;
    @JsonProperty("peerChoking")
    private Boolean peerChoking;
    @JsonProperty("peerClientName")
    private String peerClientName;
    @JsonProperty("peerId")
    private String peerId;
    @JsonProperty("peerInterested")
    private Boolean peerInterested;
    @JsonProperty("port")
    private Integer port;
    @JsonProperty("progress")
    private Double progress;
    @JsonProperty("seeder")
    private Boolean seeder;
    @JsonProperty("snubbed")
    private Boolean snubbed;
    @JsonProperty("uploadSpeed")
    private Long uploadSpeed;
    @JsonProperty("uploaded")
    private Long uploaded;
    private transient PeerAddress peerAddress;

    @Override
    public @NotNull PeerAddress getPeerAddress() {
        if (this.peerAddress == null) {
            this.peerAddress = new PeerAddress(ip, port, ip);
        }
        return this.peerAddress;
    }

    public String getPeerId() {
        return URLDecoder.decode(peerId, StandardCharsets.ISO_8859_1);
    }

    @Override
    public @Nullable String getClientName() {
        return peerClientName == null ? "" : peerClientName;
    }

    @Override
    public PeerFlag getFlags() {
        return PeerFlag.builder()
                .choked(amChoking)
                .remoteChoked(peerChoking)
                .interesting(amInterested)
                .remoteInterested(peerInterested)
                .remoteInterested(peerInterested)
                .handshake(handshaking)
                .localConnection(!incoming)
                .snubbed(snubbed)
                .seed(seeder)
                .build();
    }

    @Override
    public boolean isHandshaking() {
        return handshaking != null && handshaking;
    }

    @Override
    public double getProgress() {
        return progress != null ? progress : -1;
    }


    @Override
    public long getDownloadSpeed() {
        return downloadSpeed != null ? downloadSpeed : -1;
    }

    @Override
    public long getDownloaded() {
        return downloaded != null ? downloaded : -1;
    }

    @Override
    public long getUploadSpeed() {
        return uploadSpeed != null ? uploadSpeed : -1;
    }

    @Override
    public long getUploaded() {
        return uploaded != null ? uploaded : -1;
    }
}
