package com.ghostchu.peerbanhelper.btn.ping;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BtnPeer {
    @SerializedName("ip_address")
    private String ipAddress;
    @SerializedName("peer_port")
    private int peerPort;
    @SerializedName("peer_id")
    private String peerId;
    @SerializedName("client_name")
    private String clientName;
    @SerializedName("torrent_identifier")
    private String torrentIdentifier;
    @SerializedName("torrent_size")
    private long torrentSize;
    @SerializedName("downloaded")
    private long downloaded;
    @SerializedName("rt_download_speed")
    private long rtDownloadSpeed;
    @SerializedName("uploaded")
    private long uploaded;
    @SerializedName("rt_upload_speed")
    private long rtUploadSpeed;
    @SerializedName("peer_progress")
    private double peerProgress;
    @SerializedName("downloader_progress")
    private double downloaderProgress;
    @SerializedName("peer_flag")
    private String peerFlag;

    public static BtnPeer from(Torrent torrent, Peer peer) {
        BtnPeer btnPeer = new BtnPeer();
        btnPeer.setIpAddress(peer.getAddress().getIp());
        btnPeer.setPeerPort(peer.getAddress().getPort());
        btnPeer.setPeerId(peer.getPeerId());
        btnPeer.setClientName(peer.getClientName());
        btnPeer.setTorrentIdentifier(torrent.getHashedIdentifier());
        btnPeer.setTorrentSize(torrent.getSize());
        btnPeer.setDownloaded(peer.getDownloaded());
        btnPeer.setRtDownloadSpeed(peer.getDownloadSpeed());
        btnPeer.setUploaded(peer.getUploaded());
        btnPeer.setRtUploadSpeed(peer.getUploadedSpeed());
        btnPeer.setPeerProgress(peer.getProgress());
        btnPeer.setDownloaderProgress(torrent.getProgress());
        btnPeer.setPeerFlag(peer.getFlags());
        return btnPeer;
    }
}
