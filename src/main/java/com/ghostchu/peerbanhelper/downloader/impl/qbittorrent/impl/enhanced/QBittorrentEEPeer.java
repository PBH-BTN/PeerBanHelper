package com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.enhanced;


import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.peer.PeerFlag;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Setter
public final class QBittorrentEEPeer implements Peer {
    @SerializedName("client")
    private String client;
    @SerializedName("connection")
    private String connection;
    @SerializedName("dl_speed")
    private long dlSpeed;
    @SerializedName("downloaded")
    private long downloaded;
    @SerializedName("files")
    private String files;
    @SerializedName("flags")
    private String flags;
    @SerializedName("ip")
    private String ip;
    @SerializedName("peer_id_client")
    private String peerIdClient;
    @SerializedName("port")
    private int port;
    @SerializedName("progress")
    private double progress;
    @SerializedName("up_speed")
    private long upSpeed;
    @SerializedName("uploaded")
    private long uploaded;
    @Getter
    @SerializedName("shadowbanned")
    private Boolean shadowBanned;
    private transient PeerAddress peerAddress;

    public QBittorrentEEPeer() {
    }

    @Override
    public @NotNull PeerAddress getPeerAddress() {
        if (this.peerAddress == null) {
            this.peerAddress = new PeerAddress(ip, port, ip);
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

    @Override
    public PeerFlag getFlags() {
        return new PeerFlag(flags);
    }

    @Override
    public boolean isHandshaking() {
        return dlSpeed <= 0 && upSpeed <= 0;
    }

    public String getConnection() {
        return connection;
    }

    public void setPeerAddress(PeerAddress peerAddress) {
        this.peerAddress = peerAddress;
    }

    @Override
    public String toString() {
        return "QBEEPeer{" +
               "client='" + client + '\'' +
               ", connection='" + connection + '\'' +
               ", dlSpeed=" + dlSpeed +
               ", downloaded=" + downloaded +
               ", files='" + files + '\'' +
               ", flags='" + flags + '\'' +
               ", ip='" + ip + '\'' +
               ", peerIdClient='" + peerIdClient + '\'' +
               ", port=" + port +
               ", progress=" + progress +
               ", upSpeed=" + upSpeed +
               ", uploaded=" + uploaded +
               ", shadowBanned=" + shadowBanned +
                ", peerAddress=" + peerAddress + '}';
    }
}
