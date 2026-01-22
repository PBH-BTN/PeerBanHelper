package com.ghostchu.peerbanhelper.databasent.table;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.net.InetAddress;
import java.time.OffsetDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
@TableName("peer_records")
public final class PeerRecordEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField(value = "address")
    private InetAddress address;
    @TableField(value = "port")
    private int port;
    @TableField(value = "torrent_id")
    private Long torrentId;
    @TableField(value = "downloader")
    private String downloader;
    @TableField(value = "peer_id")
    private String peerId;
    @TableField(value = "client_name")
    private String clientName;
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
}
