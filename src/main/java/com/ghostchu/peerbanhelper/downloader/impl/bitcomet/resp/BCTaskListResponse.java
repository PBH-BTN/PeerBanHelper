package com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
public class BCTaskListResponse {

    @JsonProperty("tasks")
    private List<TasksDTO> tasks;
    @JsonProperty("version")
    private String version;

    @NoArgsConstructor
    @Data
    public static class TasksDTO {
        @JsonProperty("task_id")
        private Integer taskId;
        @JsonProperty("task_guid")
        private String taskGuid;
        @JsonProperty("type")
        private String type;
        @JsonProperty("task_name")
        private String taskName;
        @JsonProperty("status")
        private String status;
        @JsonProperty("total_size")
        private Integer totalSize;
        @JsonProperty("selected_size")
        private Integer selectedSize;
        @JsonProperty("selected_downloaded_size")
        private Integer selectedDownloadedSize;
        @JsonProperty("download_rate")
        private Integer downloadRate;
        @JsonProperty("upload_rate")
        private Integer uploadRate;
        @JsonProperty("error_code")
        private String errorCode;
        @JsonProperty("error_message")
        private String errorMessage;
        @JsonProperty("permillage")
        private Integer permillage;
        @JsonProperty("left_time")
        private String leftTime;
    }
}
