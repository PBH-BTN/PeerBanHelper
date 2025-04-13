package com.ghostchu.peerbanhelper.btn.ping;

import com.ghostchu.peerbanhelper.database.table.tmp.TrackedPeerEntity;
import com.ghostchu.peerbanhelper.util.InfoHashUtil;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BtnTrackedPeer {
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

    public static BtnTrackedPeer from(TrackedPeerEntity entity) {
        BtnTrackedPeer btnTrackedPeer = new BtnTrackedPeer();
        btnTrackedPeer.setId(entity.getId());
        btnTrackedPeer.setIp(entity.getIp());
        btnTrackedPeer.setPort(entity.getPort());
        btnTrackedPeer.setTorrentIdentifier(InfoHashUtil.getHashedIdentifier(entity.getInfoHash()));
        btnTrackedPeer.setDownloader(entity.getDownloader());
        btnTrackedPeer.setDownloaderProgress(entity.getDownloaderProgress());
        btnTrackedPeer.setPeerId(entity.getPeerId());
        btnTrackedPeer.setPeerProgress(entity.getPeerProgress());
        btnTrackedPeer.setClientName(entity.getClientName());
        btnTrackedPeer.setUploaded(entity.getUploaded());
        btnTrackedPeer.setUploadedOffset(entity.getUploadedOffset());
        btnTrackedPeer.setDownloaded(entity.getDownloaded());
        btnTrackedPeer.setDownloadedOffset(entity.getDownloadedOffset());
        btnTrackedPeer.setLastFlags(entity.getLastFlags());
        btnTrackedPeer.setFirstTimeSeen(entity.getFirstTimeSeen());
        btnTrackedPeer.setLastTimeSeen(entity.getLastTimeSeen());
        return btnTrackedPeer;
    }
}
