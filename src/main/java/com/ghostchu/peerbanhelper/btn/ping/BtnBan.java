package com.ghostchu.peerbanhelper.btn.ping;

import com.ghostchu.peerbanhelper.util.InfoHashUtil;
import com.ghostchu.peerbanhelper.wrapper.PeerWrapper;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public final class BtnBan {
    @SerializedName("module")
    private String module;
    @SerializedName("rule")
    private String rule;
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
    @SerializedName("ban_at")
    private Timestamp banAt;

    public static BtnBan from(String module, String rule, Timestamp banAt, TorrentWrapper torrent, PeerWrapper peer) {
        BtnBan btnBan = new BtnBan();
        btnBan.setModule(module);
        btnBan.setRule(rule);
        btnBan.setIpAddress(peer.getAddress().getIp());
        btnBan.setPeerPort(peer.getAddress().getPort());
        btnBan.setPeerId(peer.getId());
        btnBan.setClientName(peer.getClientName());
        btnBan.setTorrentIdentifier(InfoHashUtil.getHashedIdentifier(torrent.getHash()));
        btnBan.setTorrentIsPrivate(torrent.isPrivateTorrent());
        btnBan.setTorrentSize(torrent.getSize());
        btnBan.setDownloaded(peer.getDownloaded());
        btnBan.setRtDownloadSpeed(peer.getDownloadSpeed());
        btnBan.setUploaded(peer.getUploaded());
        btnBan.setRtUploadSpeed(peer.getUploadSpeed());
        btnBan.setPeerProgress(peer.getProgress());
        btnBan.setDownloaderProgress(torrent.getProgress());
        btnBan.setPeerFlag(peer.getFlags() == null ? null : peer.getFlags());
        btnBan.setBanAt(banAt);
        return btnBan;
    }
}
