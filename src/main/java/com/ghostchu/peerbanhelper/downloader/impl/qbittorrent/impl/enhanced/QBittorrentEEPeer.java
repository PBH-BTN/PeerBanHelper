package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.enhanced;


import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.peer.PeerFlag;
import com.ghostchu.peerbanhelper.peer.PeerMessage;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@Setter
public final class QBittorrentEEPeer implements Peer {
    @SerializedName("client")
    private String client;
    @SerializedName("connection")
    private String connection;
    @SerializedName("country")
    private String country;
    @SerializedName("country_code")
    private String countryCode;
    @SerializedName("dl_speed")
    private Long dlSpeed;
    @SerializedName("downloaded")
    private Long downloaded;
    @SerializedName("files")
    private String files;
    @SerializedName("flags")
    private String flags;
    @SerializedName("flags_desc")
    private String flagsDesc;
    @SerializedName("ip")
    private String ip;
    @SerializedName("peer_id_client")
    private String peerIdClient;
    @SerializedName("port")
    private Integer port;
    @SerializedName("progress")
    private Double progress;
    @SerializedName("relevance")
    private Double relevance;
    @SerializedName("up_speed")
    private Long upSpeed;
    @SerializedName("uploaded")
    private Long uploaded;
    @Getter
    @SerializedName("shadowbanned")
    private Boolean shadowBanned;
    private transient PeerAddress peerAddress;
    private String rawIp;

    public QBittorrentEEPeer() {
    }

    @Override
    public PeerAddress getPeerAddress() {
        if (this.peerAddress == null) {
            this.peerAddress = new PeerAddress(ip, port);
        }
        return this.peerAddress;
    }

    @Override
    @Nullable
    public String getPeerId() {
        return peerIdClient;
    }

    @Override
    @Nullable
    public String getClientName() {
        return client;
    }

    @Override
    public long getDownloadSpeed() {
        return dlSpeed;
    }

    @Override
    public long getDownloaded() {
        return downloaded;
    }

    @Override
    public long getUploadSpeed() {
        return upSpeed;
    }

    @Override
    public long getUploaded() {
        return uploaded;
    }

    @Override
    public double getProgress() {
        return progress;
    }

    /**
     * Retrieves the peer flags as a {@code PeerFlag} object.
     *
     * @return A {@code PeerFlag} object constructed from the peer's flags string
     */
    @Override
    public PeerFlag getFlags() {
        return new PeerFlag(flags);
    }

    /**
     * Determines if the peer is currently in a handshaking state.
     *
     * A peer is considered to be in a handshaking state when both download and upload speeds
     * are zero or negative, typically indicating that the peer connection is being established
     * but no data transfer has begun.
     *
     * @return {@code true} if the peer is in a handshaking state (no active data transfer),
     *         {@code false} otherwise
     */
    @Override
    public boolean isHandshaking() {
        return dlSpeed <= 0 && upSpeed <= 0;
    }

    /**
     * Returns an empty list of supported peer messages.
     *
     * @return An unmodifiable empty list indicating no specific peer messages are supported by this peer.
     */
    @Override
    public List<PeerMessage> getSupportedMessages() {
        return Collections.emptyList();
    }

    @Override
    public String getRawIp() {
        return rawIp == null ? ip : rawIp;
    }

    public String getConnection() {
        return connection;
    }

    @Override
    public String toString() {
        return "QBEEPeer{" +
               "client='" + client + '\'' +
               ", connection='" + connection + '\'' +
               ", country='" + country + '\'' +
               ", countryCode='" + countryCode + '\'' +
               ", dlSpeed=" + dlSpeed +
               ", downloaded=" + downloaded +
               ", files='" + files + '\'' +
               ", flags='" + flags + '\'' +
               ", flagsDesc='" + flagsDesc + '\'' +
               ", ip='" + ip + '\'' +
               ", peerIdClient='" + peerIdClient + '\'' +
               ", port=" + port +
               ", progress=" + progress +
               ", relevance=" + relevance +
               ", upSpeed=" + upSpeed +
               ", uploaded=" + uploaded +
               ", shadowBanned=" + shadowBanned +
               ", peerAddress=" + peerAddress +
               ", rawIp='" + rawIp + '\'' +
               '}';
    }
}
