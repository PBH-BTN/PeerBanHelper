package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent;


import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    public void setClient(String client) {
        this.client = client;
    }

    public void setConnection(String connection) {
        this.connection = connection;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public void setDlSpeed(Long dlSpeed) {
        this.dlSpeed = dlSpeed;
    }

    public void setDownloaded(Long downloaded) {
        this.downloaded = downloaded;
    }

    public void setFiles(String files) {
        this.files = files;
    }

    public void setFlags(String flags) {
        this.flags = flags;
    }

    public void setFlagsDesc(String flagsDesc) {
        this.flagsDesc = flagsDesc;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setPeerIdClient(String peerIdClient) {
        this.peerIdClient = peerIdClient;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public void setProgress(Double progress) {
        this.progress = progress;
    }

    public void setRelevance(Double relevance) {
        this.relevance = relevance;
    }

    public void setUpSpeed(Long upSpeed) {
        this.upSpeed = upSpeed;
    }

    public void setUploaded(Long uploaded) {
        this.uploaded = uploaded;
    }
}
