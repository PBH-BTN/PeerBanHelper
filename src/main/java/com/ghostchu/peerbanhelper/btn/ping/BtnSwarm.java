package com.ghostchu.peerbanhelper.btn.ping;

import com.ghostchu.peerbanhelper.database.table.tmp.TrackedSwarmEntity;
import com.ghostchu.peerbanhelper.util.InfoHashUtil;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BtnSwarm {
    private transient long id;
    @SerializedName("torrent_identifier")
    private String torrentIdentifier;
    @SerializedName("torrent_is_private")
    private Boolean torrentIsPrivate;
    @SerializedName("torrent_size")
    private long torrentSize;
    @SerializedName("downloader")
    private String downloader;
    @SerializedName("downloader_progress")
    private double downloaderProgress;
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
    @SerializedName("to_peer_traffic")
    private long toPeerTraffic;
    @SerializedName("to_peer_traffic_offset")
    private long toPeerTrafficOffset;
    @SerializedName("from_peer_traffic")
    private long fromPeerTraffic;
    @SerializedName("from_peer_traffic_offset")
    private long fromPeerTrafficOffset;
    @SerializedName("first_time_seen")
    private Timestamp firstTimeSeen;
    @SerializedName("last_time_seen")
    private Timestamp lastTimeSeen;
    @SerializedName("peer_last_flags")
    private String peerLastFlags;

    public static BtnSwarm from(TrackedSwarmEntity entity) {
        BtnSwarm btnSwarm = new BtnSwarm();
        btnSwarm.setId(entity.getId());
        btnSwarm.setPeerIp(entity.getIp());
        btnSwarm.setPeerPort(entity.getPort());
        btnSwarm.setTorrentIdentifier(InfoHashUtil.getHashedIdentifier(entity.getInfoHash()));
        btnSwarm.setTorrentIsPrivate(entity.getTorrentIsPrivate());
        btnSwarm.setTorrentSize(entity.getTorrentSize());
        btnSwarm.setDownloader(entity.getDownloader());
        btnSwarm.setDownloaderProgress(entity.getDownloaderProgress());
        btnSwarm.setPeerId(entity.getPeerId());
        btnSwarm.setPeerProgress(entity.getPeerProgress());
        btnSwarm.setPeerClientName(entity.getClientName());
        btnSwarm.setToPeerTraffic(entity.getUploaded());
        btnSwarm.setToPeerTrafficOffset(entity.getUploadedOffset());
        btnSwarm.setFromPeerTraffic(entity.getDownloaded());
        btnSwarm.setFromPeerTrafficOffset(entity.getDownloadedOffset());
        btnSwarm.setPeerLastFlags(entity.getLastFlags());
        btnSwarm.setFirstTimeSeen(entity.getFirstTimeSeen());
        btnSwarm.setLastTimeSeen(entity.getLastTimeSeen());
        return btnSwarm;
    }
}
