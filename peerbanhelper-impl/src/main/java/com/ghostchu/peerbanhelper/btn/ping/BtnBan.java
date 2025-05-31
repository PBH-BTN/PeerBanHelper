package com.ghostchu.peerbanhelper.btn.ping;

import com.ghostchu.peerbanhelper.database.table.HistoryEntity;
import com.ghostchu.peerbanhelper.common.util.InfoHashUtil;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

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
    @SerializedName("uploaded")
    private long uploaded;
    @SerializedName("peer_progress")
    private double peerProgress;
    @SerializedName("downloader_progress")
    private double downloaderProgress;
    @SerializedName("peer_flag")
    private String peerFlag;
    @SerializedName("ban_at")
    private Timestamp banAt;

    public static BtnBan from(HistoryEntity historyEntity) {
        BtnBan btnBan = new BtnBan();
        btnBan.setModule(historyEntity.getRule().getModule().getName());
        btnBan.setRule(tlUI(historyEntity.getRule().getRule()));
        btnBan.setIpAddress(historyEntity.getIp());
        btnBan.setPeerPort(historyEntity.getPort());
        btnBan.setPeerId(historyEntity.getPeerId());
        btnBan.setClientName(historyEntity.getPeerClientName());
        btnBan.setTorrentIdentifier(InfoHashUtil.getHashedIdentifier(historyEntity.getTorrent().getInfoHash()));
        btnBan.setTorrentIsPrivate(Boolean.TRUE.equals(historyEntity.getTorrent().getPrivateTorrent()));
        btnBan.setTorrentSize(historyEntity.getTorrent().getSize());
        btnBan.setDownloaded(historyEntity.getPeerDownloaded());
        btnBan.setUploaded(historyEntity.getPeerUploaded());
        btnBan.setPeerProgress(historyEntity.getPeerProgress());
        btnBan.setDownloaderProgress(historyEntity.getDownloaderProgress());
        btnBan.setPeerFlag(historyEntity.getFlags() == null ? null : historyEntity.getFlags());
        btnBan.setBanAt(historyEntity.getBanAt());
        return btnBan;
    }
}
