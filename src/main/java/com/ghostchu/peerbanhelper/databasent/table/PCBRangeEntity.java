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
@TableName("pcb_range")
public final class PCBRangeEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField(value = "range")
    private String range;
    @TableField(value = "torrent_id")
    private String torrentId;
    @TableField(value = "last_report_progress")
    private double lastReportProgress;
    @TableField(value = "last_report_uploaded")
    private long lastReportUploaded;
    @TableField(value = "tracking_uploaded_increase_total")
    private long trackingUploadedIncreaseTotal;
    @TableField(value = "rewind_counter")
    private int rewindCounter;
    @TableField(value = "progress_difference_counter")
    private int progressDifferenceCounter;
    @TableField(value = "first_time_seen")
    private OffsetDateTime firstTimeSeen;
    @TableField(value = "last_time_seen")
    private OffsetDateTime lastTimeSeen;
    @TableField(value = "downloader")
    private String downloader;
    @TableField(value = "ban_delay_window_end_at")
    private OffsetDateTime banDelayWindowEndAt;
    @TableField(value = "fast_pcb_test_execute_at")
    private long fastPcbTestExecuteAt;
    @TableField(value = "last_torrent_completed_size")
    private long lastTorrentCompletedSize;
}
