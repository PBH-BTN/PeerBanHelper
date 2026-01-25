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
@TableName(value = "peer_connection_metrics_track", autoResultMap = true)
public final class PeerConnectionMetricsTrackEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField(value = "timeframe_at")
    private OffsetDateTime timeframeAt;
    @TableField(value = "downloader")
    private String downloader;
    @TableField(value = "torrent_id")
    private Long torrentId;
    @TableField(value = "address")
    private InetAddress address;
    @TableField(value = "port")
    private int port;
    @TableField(value = "peer_id")
    private String peerId;
    @TableField(value = "client_name")
    private String clientName;
    @TableField(value = "last_flags")
    private String lastFlags;

}
