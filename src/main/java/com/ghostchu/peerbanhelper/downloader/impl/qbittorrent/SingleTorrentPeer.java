package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;


import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.gson.annotations.SerializedName;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Setter
public class SingleTorrentPeer implements Peer {
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

    public SingleTorrentPeer() {
    }

    @Override
    public PeerAddress getAddress() {
        return new PeerAddress(ip, port);
    }

    @Override
    @NotNull
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
    public long getUploadedSpeed() {
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

    @Override
    public String getFlags() {
        return flags;
    }

    @Override
    public String toString() {
        return "SingleTorrentPeer{" +
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
                '}';
    }

}
