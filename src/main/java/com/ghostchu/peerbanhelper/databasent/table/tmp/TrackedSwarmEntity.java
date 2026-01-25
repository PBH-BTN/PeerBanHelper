package com.ghostchu.peerbanhelper.databasent.table.tmp;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.net.InetAddress;
import java.time.OffsetDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
@TableName("tracked_swarm")
public final class TrackedSwarmEntity { // 需要创建为临时表
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

}
