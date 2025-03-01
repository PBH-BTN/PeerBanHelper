package com.ghostchu.peerbanhelper.downloader.impl.bitcomet.resp;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
public final class BCTaskListResponse {

    @SerializedName("tasks")
    private List<TasksDTO> tasks;

    @NoArgsConstructor
    @Data
    public static class TasksDTO {
        @SerializedName("task_id")
        private long taskId;
        @SerializedName("type")
        private String type;
    }
}
