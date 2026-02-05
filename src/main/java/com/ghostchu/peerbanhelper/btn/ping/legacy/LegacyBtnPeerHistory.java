package com.ghostchu.peerbanhelper.btn.ping.legacy;

import com.ghostchu.peerbanhelper.databasent.table.PeerRecordEntity;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.TorrentEntityDTO;
import com.ghostchu.peerbanhelper.util.InfoHashUtil;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public final class LegacyBtnPeerHistory {
    @SerializedName("ip_address")
    private String ipAddress;
    @SerializedName("port")
    private int port;
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
    @SerializedName("downloaded_offset")
    private long downloadedOffset;
    @SerializedName("uploaded")
    private long uploaded;
    @SerializedName("uploaded_offset")
    private long uploadedOffset;
    @SerializedName("first_time_seen")
    private Timestamp firstTimeSeen;
    @SerializedName("last_time_seen")
    private Timestamp lastTimeSeen;
    @SerializedName("peer_flag")
    private String peerFlag;

    public static LegacyBtnPeerHistory from(PeerRecordEntity peer, TorrentEntityDTO torrentEntityDTO) {
        LegacyBtnPeerHistory btnPeer = new LegacyBtnPeerHistory();
        btnPeer.setIpAddress(peer.getAddress().getHostAddress());
        btnPeer.setPeerId(peer.getPeerId());
        btnPeer.setClientName(peer.getClientName());
        String hashedId = InfoHashUtil.getHashedIdentifier(torrentEntityDTO.infoHash());
        btnPeer.setTorrentIdentifier(hashedId);
        btnPeer.setTorrentSize(torrentEntityDTO.size());
        btnPeer.setDownloaded(peer.getDownloaded());
        btnPeer.setDownloadedOffset(peer.getDownloadedOffset());
        btnPeer.setUploaded(peer.getUploaded());
        btnPeer.setUploadedOffset(peer.getUploadedOffset());
        btnPeer.setFirstTimeSeen(new Timestamp(peer.getFirstTimeSeen().toInstant().toEpochMilli()));
        btnPeer.setLastTimeSeen(new Timestamp(peer.getLastTimeSeen().toInstant().toEpochMilli()));
        btnPeer.setPeerFlag(peer.getLastFlags() == null ? null : peer.getLastFlags());
        return btnPeer;
    }
}