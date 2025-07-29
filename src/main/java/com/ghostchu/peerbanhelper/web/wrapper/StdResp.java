package com.ghostchu.peerbanhelper.web.wrapper;

import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTask;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
public class StdResp {
    private final boolean success;
    private final @Nullable BackgroundTaskInfo backgroundTask;
    private final @Nullable String message;
    private final @Nullable Object data;

    public StdResp(boolean success, @Nullable String message, @Nullable Object data){
        this.success = success;
        this.backgroundTask = null;
        this.message = message;
        this.data = data;
    }
    public StdResp(boolean success, @Nullable String message, boolean showLogs, BackgroundTask task){
        this.success = success;
        this.backgroundTask = new BackgroundTaskInfo(task.getId(), showLogs);
        this.message = message;
        this.data = null;
    }

    public record BackgroundTaskInfo (String id, boolean showLogs) {
    }
}
