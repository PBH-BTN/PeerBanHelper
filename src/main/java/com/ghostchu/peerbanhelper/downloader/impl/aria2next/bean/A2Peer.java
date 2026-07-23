package com.ghostchu.peerbanhelper.downloader.impl.aria2next.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.peer.PeerFlag;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@NoArgsConstructor
@Data
public class A2Peer implements Peer {

    @JsonProperty("amChoking")
    private boolean amChoking;
    @JsonProperty("amInterested")
    private boolean amInterested;
    @JsonProperty("bitfield")
    private String bitfield;
    @JsonProperty("completedLength")
    private long completedLength;
    @JsonProperty("downloadSpeed")
    private long downloadSpeed;
    @JsonProperty("downloaded")
    private long downloaded;
    @JsonProperty("flags")
    private String flags;
    @JsonProperty("handshaking")
    private boolean handshaking;
    @JsonProperty("incoming")
    private boolean incoming;
    @JsonProperty("ip")
    private String ip;
    @JsonProperty("optimisticUnchoke")
    private boolean optimisticUnchoke;
    @JsonProperty("peerChoking")
    private boolean peerChoking;
    @JsonProperty("peerClientName")
    private String peerClientName;
    @JsonProperty("peerId")
    private String peerId;
    @JsonProperty("peerInterested")
    private boolean peerInterested;
    @JsonProperty("port")
    private int port;
    @JsonProperty("progress")
    private double progress;
    @JsonProperty("seeder")
    private boolean seeder;
    @JsonProperty("snubbed")
    private boolean snubbed;
    @JsonProperty("uploadSpeed")
    private long uploadSpeed;
    @JsonProperty("uploaded")
    private long uploaded;
    private transient PeerAddress peerAddress;

    @Override
    public @NotNull PeerAddress getPeerAddress() {
        if (this.peerAddress == null) {
            this.peerAddress = new PeerAddress(ip, port, ip);
        }
        return this.peerAddress;
    }

    @Override
    public @Nullable String getClientName() {
        return peerClientName;
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
}
