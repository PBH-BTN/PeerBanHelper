package com.ghostchu.peerbanhelper.btn.ping;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.InfoHashUtil;
import com.ghostchu.peerbanhelper.wrapper.PeerWrapper;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public final class BtnPeer {
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
    @SerializedName("torrent_is_private")
    private boolean torrentIsPrivate;
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
        return from(new TorrentWrapper(torrent), new PeerWrapper(peer));
    }

    public static BtnPeer from(TorrentWrapper torrent, PeerWrapper peer) {
        BtnPeer btnPeer = new BtnPeer();
        btnPeer.setIpAddress(peer.getAddress().getIp());
        btnPeer.setPeerPort(peer.getAddress().getPort());
        btnPeer.setPeerId(peer.getId());
        btnPeer.setClientName(peer.getClientName());
        String hashedId = InfoHashUtil.getHashedIdentifier(torrent.getHash());
        btnPeer.setTorrentIdentifier(hashedId);
        btnPeer.setTorrentIsPrivate(torrent.isPrivateTorrent());
        btnPeer.setTorrentSize(torrent.getSize());
        btnPeer.setDownloaded(peer.getDownloaded());
        btnPeer.setRtDownloadSpeed(peer.getDownloadSpeed());
        btnPeer.setUploaded(peer.getUploaded());
        btnPeer.setRtUploadSpeed(peer.getUploadSpeed());
        btnPeer.setPeerProgress(peer.getProgress());
        btnPeer.setDownloaderProgress(torrent.getProgress());
        btnPeer.setPeerFlag(peer.getFlags() == null ? null : peer.getFlags());
        return btnPeer;
    }
}
