package com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
public class BCTaskListResponse {

    @SerializedName("tasks")
    private List<TasksDTO> tasks;
//    @SerializedName("version")
//    private String version;

    @NoArgsConstructor
    @Data
    public static class TasksDTO {
        @SerializedName("task_id")
        private long taskId;
        @SerializedName("type")
        private String type;
//        @SerializedName("task_name")
//        private String taskName;
//        @SerializedName("status")
//        private String status;
//        @SerializedName("total_size")
//        private Long totalSize;
//        @SerializedName("selected_size")
//        private Long selectedSize;
//        @SerializedName("selected_downloaded_size")
//        private Long selectedDownloadedSize;
//        @SerializedName("download_rate")
//        private Long downloadRate;
//        @SerializedName("upload_rate")
//        private Long uploadRate;
//        @SerializedName("error_code")
//        private String errorCode;
//        @SerializedName("error_message")
//        private String errorMessage;
//        @SerializedName("permillage")
//        private Long permillage;
//        @SerializedName("left_time")
//        private String leftTime;
    }
}
