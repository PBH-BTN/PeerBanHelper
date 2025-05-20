package com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public final class BCTaskTorrentResponse {

    @SerializedName("error_code")
    private String errorCode;
    @SerializedName("task_detail")
    private TaskDetailDTO taskDetail;
    @SerializedName("task_status")
    private TaskStatusDTO taskStatus;
    @SerializedName("task_summary")
    private TaskSummaryDTO taskSummary;
    @SerializedName("task")
    private TaskDTO task;

    @NoArgsConstructor
    @Data
    public static class TaskDetailDTO {
        @SerializedName("type")
        private String type;
        @SerializedName("infohash")
        private String infohash;
        @SerializedName("infohash_v2")
        private String infohashV2;
        @SerializedName("download_link")
        private String downloadLink;
        @SerializedName("task_name")
        private String taskName;
        @SerializedName("total_size")
        private long totalSize;
        @SerializedName("torrent_private")
        private Boolean torrentPrivate;
    }

    @NoArgsConstructor
    @Data
    public static class TaskStatusDTO {
        @SerializedName("status")
        private String status;
        @SerializedName("dl_size")
        private long dlSize;
        @SerializedName("up_size")
        private long upSize;
        @SerializedName("total_size")
        private long totalSize;
        @SerializedName("download_permillage")
        private short downloadPermillage;
    }

    @NoArgsConstructor
    @Data
    public static class TaskSummaryDTO {
        @SerializedName("tags")
        private String tags;
    }

    @NoArgsConstructor
    @Data
    public static class TaskDTO {
        @SerializedName("task_id")
        private long taskId;
        @SerializedName("type")
        private String type;
        @SerializedName("task_name")
        private String taskName;
        @SerializedName("status")
        private String status;
        @SerializedName("total_size")
        private long totalSize;
        @SerializedName("selected_downloaded_size")
        private long selectedDownloadedSize;
        @SerializedName("download_rate")
        private long downloadRate;
        @SerializedName("upload_rate")
        private long uploadRate;
        @SerializedName("permillage")
        private short permillage;
    }
}
