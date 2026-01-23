package com.ghostchu.peerbanhelper.databasent.table;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ghostchu.peerbanhelper.databasent.converter.TranslationComponentTypeHandler;
import com.ghostchu.peerbanhelper.databasent.driver.common.JsonTypeHandlerForwarder;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.ipdb.IPGeoData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
@TableName("history")
public final class HistoryEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField(value = "ban_at")
    private OffsetDateTime banAt;
    @TableField(value = "unban_at")
    private OffsetDateTime unbanAt;
    @TableField(value = "ip")
    private InetAddress ip;
    @TableField(value = "port")
    private Integer port;
    @TableField(value = "peer_id")
    private String peerId;
    @TableField(value = "peer_client_name")
    private String peerClientName;
    @TableField(value = "peer_uploaded")
    private Long peerUploaded;
    @TableField(value = "peer_downloaded")
    private Long peerDownloaded;
    @TableField(value = "peer_progress")
    private Double peerProgress;
    @TableField(value = "downloader_progress")
    private Double downloaderProgress;
    @TableField(value = "torrent_id")
    private Long torrentId;
    @TableField(value = "module_name")
    private String moduleName;
    @TableField(value = "rule_name")
    private String ruleName;
    @TableField(value = "description", typeHandler = TranslationComponentTypeHandler.class)
    private TranslationComponent description;
    @TableField(value = "flags")
    private String flags;
    @TableField(value = "downloader")
    private String downloader;
    @TableField(value = "structured_data")
    private Map<String, Object> structuredData;
    @TableField(value = "peer_geoip", typeHandler = JsonTypeHandlerForwarder.class)
    private IPGeoData peerGeoIp;
}
