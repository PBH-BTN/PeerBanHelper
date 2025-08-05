package com.ghostchu.peerbanhelper.web.wrapper;

import com.ghostchu.peerbanhelper.util.asynctask.AsyncTask;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
public class StdResp {
    private final boolean success;
    private final @Nullable AsyncTaskDTO asyncTask;
    private final @Nullable String message;
    private final @Nullable Object data;

    public StdResp(boolean success, @Nullable String message, @Nullable Object data){
        this.success = success;
        this.asyncTask = null;
        this.message = message;
        this.data = data;
    }

    public StdResp(boolean success, @Nullable String message, boolean showLogs, AsyncTask task) {
        this.success = success;
        this.asyncTask = new AsyncTaskDTO(task.getTaskId(), showLogs);
        this.message = message;
        this.data = null;
    }

    public record AsyncTaskDTO(String id, boolean showLogs) {
    }
}
