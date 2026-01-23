package com.ghostchu.peerbanhelper.btn.ping;

import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.TorrentEntityDTO;
import com.ghostchu.peerbanhelper.util.InfoHashUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
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
    @SerializedName("ban_at")
    private Timestamp banAt;
    @SerializedName("peer_ip")
    private String peerIp;
    @SerializedName("peer_port")
    private int peerPort;
    @SerializedName("peer_id")
    private String peerId;
    @SerializedName("peer_client_name")
    private String peerClientName;
    @SerializedName("peer_progress")
    private double peerProgress;
    @SerializedName("peer_flag")
    private String peerFlag;
    @SerializedName("torrent_identifier")
    private String torrentIdentifier;
    @SerializedName("torrent_is_private")
    private boolean torrentIsPrivate;
    @SerializedName("torrent_size")
    private long torrentSize;
    @SerializedName("from_peer_traffic")
    private long fromPeerTraffic;
    @SerializedName("to_peer_traffic")
    private long toPeerTraffic;
    @SerializedName("downloader_progress")
    private double downloaderProgress;
    @SerializedName("module")
    private String module;
    @SerializedName("rule")
    private String rule;
    @SerializedName("description")
    private String description;
    @SerializedName("structured_data")
    private String structuredData;

    public static BtnBan from(HistoryEntity historyEntity, TorrentEntityDTO torrentEntityDTO) {
        BtnBan btnBan = new BtnBan();
        btnBan.setModule(historyEntity.getModuleName());
        btnBan.setRule(tlUI(historyEntity.getRuleName()));
        btnBan.setDescription(tlUI(historyEntity.getDescription()));
        btnBan.setPeerIp(historyEntity.getIp().getHostAddress());
        btnBan.setPeerPort(historyEntity.getPort());
        btnBan.setPeerId(historyEntity.getPeerId());
        btnBan.setPeerClientName(historyEntity.getPeerClientName());
        btnBan.setTorrentIdentifier(InfoHashUtil.getHashedIdentifier(torrentEntityDTO.infoHash()));
        btnBan.setTorrentIsPrivate(Boolean.TRUE.equals(torrentEntityDTO.privateTorrent()));// can be null
        btnBan.setTorrentSize(torrentEntityDTO.size());
        btnBan.setFromPeerTraffic(historyEntity.getPeerDownloaded());
        btnBan.setToPeerTraffic(historyEntity.getPeerUploaded());
        btnBan.setPeerProgress(historyEntity.getPeerProgress());
        btnBan.setDownloaderProgress(historyEntity.getDownloaderProgress());
        btnBan.setPeerFlag(historyEntity.getFlags() == null ? null : historyEntity.getFlags());
        btnBan.setBanAt(new Timestamp(historyEntity.getBanAt().toInstant().toEpochMilli()));
        btnBan.setStructuredData(JsonUtil.standard().toJson(historyEntity.getStructuredData()));
        return btnBan;
    }
}
