package com.ghostchu.peerbanhelper.btn.ping;

import com.ghostchu.peerbanhelper.database.table.PeerRecordEntity;
import com.ghostchu.peerbanhelper.util.InfoHashUtil;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public final class BtnPeerHistory {
    @SerializedName("ip_address")
    private String ipAddress;
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

    public static BtnPeerHistory from(PeerRecordEntity peer) {
        BtnPeerHistory btnPeer = new BtnPeerHistory();
        btnPeer.setIpAddress(peer.getAddress());
        btnPeer.setPeerId(peer.getPeerId());
        btnPeer.setClientName(peer.getClientName());
        String hashedId = InfoHashUtil.getHashedIdentifier(peer.getTorrent().getInfoHash());
        btnPeer.setTorrentIdentifier(hashedId);
        btnPeer.setTorrentSize(peer.getTorrent().getSize());
        btnPeer.setDownloaded(peer.getDownloaded());
        btnPeer.setDownloadedOffset(peer.getDownloadedOffset());
        btnPeer.setUploaded(peer.getUploaded());
        btnPeer.setUploadedOffset(peer.getUploadedOffset());
        btnPeer.setFirstTimeSeen(peer.getFirstTimeSeen());
        btnPeer.setLastTimeSeen(peer.getLastTimeSeen());
        btnPeer.setPeerFlag(peer.getLastFlags() == null ? null : peer.getLastFlags());
        return btnPeer;
    }
}
