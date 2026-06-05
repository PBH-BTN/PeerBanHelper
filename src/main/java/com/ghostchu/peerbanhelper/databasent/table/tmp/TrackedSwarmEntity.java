package com.ghostchu.peerbanhelper.databasent.table.tmp;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ghostchu.peerbanhelper.databasent.table.AbstractCanDirtyEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.net.InetAddress;
import java.time.OffsetDateTime;

@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
@TableName("tracked_swarm")
public final class TrackedSwarmEntity extends AbstractCanDirtyEntity implements Serializable { // 需要创建为临时表
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField(value = "ip")
    private InetAddress ip;
    @TableField(value = "port")
    private int port;
    @TableField(value = "info_hash")
    private String infoHash;
    @TableField(value = "torrent_is_private")
    private Boolean torrentIsPrivate;
    @TableField(value = "torrent_size")
    private long torrentSize;
    @TableField(value = "downloader")
    private String downloader;
    @TableField(value = "downloader_progress")
    private double downloaderProgress;
    @TableField(value = "peer_id")
    private String peerId;
    @TableField(value = "client_name")
    private String clientName;
    @TableField(value = "peer_progress")
    private double peerProgress;
    @TableField(value = "uploaded")
    private long uploaded;
    @TableField(value = "uploaded_offset")
    private long uploadedOffset;
    @TableField(value = "upload_speed")
    private long uploadSpeed;
    @TableField(value = "downloaded")
    private long downloaded;
    @TableField(value = "downloaded_offset")
    private long downloadedOffset;
    @TableField(value = "download_speed")
    private long downloadSpeed;
    @TableField(value = "last_flags")
    private String lastFlags;
    @TableField(value = "first_time_seen")
    private OffsetDateTime firstTimeSeen;
    @TableField(value = "last_time_seen")
    private OffsetDateTime lastTimeSeen;
    @TableField(value = "download_speed_max")
    private long downloadSpeedMax;
    @TableField(value = "upload_speed_max")
    private long uploadSpeedMax;


    public void setId(Long id) {
        this.id = id;
        setDirty(true);
    }

    public void setIp(InetAddress ip) {
        this.ip = ip;
        setDirty(true);
    }

    public void setPort(int port) {
        this.port = port;
        setDirty(true);
    }

    public void setInfoHash(String infoHash) {
        this.infoHash = infoHash;
        setDirty(true);
    }

    public void setTorrentIsPrivate(Boolean torrentIsPrivate) {
        this.torrentIsPrivate = torrentIsPrivate;
        setDirty(true);
    }

    public void setTorrentSize(long torrentSize) {
        this.torrentSize = torrentSize;
        setDirty(true);
    }

    public void setDownloader(String downloader) {
        this.downloader = downloader;
        setDirty(true);
    }

    public void setDownloaderProgress(double downloaderProgress) {
        this.downloaderProgress = downloaderProgress;
        setDirty(true);
    }

    public void setPeerId(String peerId) {
        this.peerId = peerId;
        setDirty(true);
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
        setDirty(true);
    }

    public void setPeerProgress(double peerProgress) {
        this.peerProgress = peerProgress;
        setDirty(true);
    }

    public void setUploaded(long uploaded) {
        this.uploaded = uploaded;
        setDirty(true);
    }

    public void setUploadedOffset(long uploadedOffset) {
        this.uploadedOffset = uploadedOffset;
        setDirty(true);
    }

    public void setUploadSpeed(long uploadSpeed) {
        this.uploadSpeed = uploadSpeed;
        setDirty(true);
    }

    public void setDownloaded(long downloaded) {
        this.downloaded = downloaded;
        setDirty(true);
    }

    public void setDownloadedOffset(long downloadedOffset) {
        this.downloadedOffset = downloadedOffset;
        setDirty(true);
    }

    public void setDownloadSpeed(long downloadSpeed) {
        this.downloadSpeed = downloadSpeed;
        setDirty(true);
    }

    public void setLastFlags(String lastFlags) {
        this.lastFlags = lastFlags;
        setDirty(true);
    }

    public void setFirstTimeSeen(OffsetDateTime firstTimeSeen) {
        this.firstTimeSeen = firstTimeSeen;
        setDirty(true);
    }

    public void setLastTimeSeen(OffsetDateTime lastTimeSeen) {
        this.lastTimeSeen = lastTimeSeen;
        setDirty(true);
    }

    public void setDownloadSpeedMax(long downloadSpeedMax) {
        this.downloadSpeedMax = downloadSpeedMax;
        setDirty(true);
    }

    public void setUploadSpeedMax(long uploadSpeedMax) {
        this.uploadSpeedMax = uploadSpeedMax;
        setDirty(true);
    }
}
