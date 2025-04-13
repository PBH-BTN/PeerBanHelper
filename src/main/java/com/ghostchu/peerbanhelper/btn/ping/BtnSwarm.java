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
    @SerializedName("ip")
    private String ip;
    @SerializedName("port")
    private int port;
    @SerializedName("torrent_identifier")
    private String torrentIdentifier;
    @SerializedName("downloader")
    private String downloader;
    @SerializedName("downloader_progress")
    private double downloaderProgress;
    @SerializedName("peer_id")
    private String peerId;
    @SerializedName("client_name")
    private String clientName;
    @SerializedName("peer_progress")
    private double peerProgress;
    @SerializedName("uploaded")
    private long uploaded;
    @SerializedName("uploaded_offset")
    private long uploadedOffset;
    @SerializedName("downloaded")
    private long downloaded;
    @SerializedName("downloaded_offset")
    private long downloadedOffset;
    @SerializedName("last_flags")
    private String lastFlags;
    @SerializedName("first_time_seen")
    private Timestamp firstTimeSeen;
    @SerializedName("last_time_seen")
    private Timestamp lastTimeSeen;

    public static BtnSwarm from(TrackedSwarmEntity entity) {
        BtnSwarm btnSwarm = new BtnSwarm();
        btnSwarm.setId(entity.getId());
        btnSwarm.setIp(entity.getIp());
        btnSwarm.setPort(entity.getPort());
        btnSwarm.setTorrentIdentifier(InfoHashUtil.getHashedIdentifier(entity.getInfoHash()));
        btnSwarm.setDownloader(entity.getDownloader());
        btnSwarm.setDownloaderProgress(entity.getDownloaderProgress());
        btnSwarm.setPeerId(entity.getPeerId());
        btnSwarm.setPeerProgress(entity.getPeerProgress());
        btnSwarm.setClientName(entity.getClientName());
        btnSwarm.setUploaded(entity.getUploaded());
        btnSwarm.setUploadedOffset(entity.getUploadedOffset());
        btnSwarm.setDownloaded(entity.getDownloaded());
        btnSwarm.setDownloadedOffset(entity.getDownloadedOffset());
        btnSwarm.setLastFlags(entity.getLastFlags());
        btnSwarm.setFirstTimeSeen(entity.getFirstTimeSeen());
        btnSwarm.setLastTimeSeen(entity.getLastTimeSeen());
        return btnSwarm;
    }
}
