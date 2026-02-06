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
import java.time.OffsetDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
@TableName(value = "traffic_journal_v3", autoResultMap = true)
public final class TrafficJournalEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField(value = "timestamp")
    private OffsetDateTime timestamp;
    @TableField(value = "downloader")
    private String downloader;
    @TableField(value = "data_overall_uploaded_at_start")
    private long dataOverallUploadedAtStart;
    @TableField(value = "data_overall_uploaded")
    private long dataOverallUploaded;
    @TableField(value = "data_overall_downloaded_at_start")
    private long dataOverallDownloadedAtStart;
    @TableField(value = "data_overall_downloaded")
    private long dataOverallDownloaded;
    @TableField(value = "protocol_overall_uploaded_at_start")
    private long protocolOverallUploadedAtStart;
    @TableField(value = "protocol_overall_uploaded")
    private long protocolOverallUploaded;
    @TableField(value = "protocol_overall_downloaded_at_start")
    private long protocolOverallDownloadedAtStart;
    @TableField(value = "protocol_overall_downloaded")
    private long protocolOverallDownloaded;
}
