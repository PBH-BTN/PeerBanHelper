package com.ghostchu.peerbanhelper.databasent.table;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ghostchu.peerbanhelper.util.helpstatus.CanDirty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
@TableName(value = "pcb_range", autoResultMap = true)
public final class PCBRangeEntity extends AbstractCanDirtyEntity implements Serializable, CanDirty {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField(value = "ip_range")
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
    private OffsetDateTime fastPcbTestExecuteAt;
    @TableField(value = "last_torrent_completed_size")
    private long lastTorrentCompletedSize;

    public void setId(Long id) {
        this.id = id;
        setDirty(true);
    }

    public void setRange(String range) {
        this.range = range;
        setDirty(true);
    }

    public void setTorrentId(String torrentId) {
        this.torrentId = torrentId;
        setDirty(true);
    }

    public void setLastReportProgress(double lastReportProgress) {
        this.lastReportProgress = lastReportProgress;
        setDirty(true);
    }

    public void setLastReportUploaded(long lastReportUploaded) {
        this.lastReportUploaded = lastReportUploaded;
        setDirty(true);
    }

    public void setTrackingUploadedIncreaseTotal(long trackingUploadedIncreaseTotal) {
        this.trackingUploadedIncreaseTotal = trackingUploadedIncreaseTotal;
        setDirty(true);
    }

    public void setRewindCounter(int rewindCounter) {
        this.rewindCounter = rewindCounter;
        setDirty(true);
    }

    public void setProgressDifferenceCounter(int progressDifferenceCounter) {
        this.progressDifferenceCounter = progressDifferenceCounter;
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

    public void setDownloader(String downloader) {
        this.downloader = downloader;
        setDirty(true);
    }

    public void setBanDelayWindowEndAt(OffsetDateTime banDelayWindowEndAt) {
        this.banDelayWindowEndAt = banDelayWindowEndAt;
        setDirty(true);
    }

    public void setFastPcbTestExecuteAt(OffsetDateTime fastPcbTestExecuteAt) {
        this.fastPcbTestExecuteAt = fastPcbTestExecuteAt;
        setDirty(true);
    }

    public void setLastTorrentCompletedSize(long lastTorrentCompletedSize) {
        this.lastTorrentCompletedSize = lastTorrentCompletedSize;
        setDirty(true);
    }
}
