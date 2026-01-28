package com.ghostchu.peerbanhelper.util.backgroundtask;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;
@Data
public class BackgroundTask implements AutoCloseable {
    @NotNull
    private TranslationComponent title;
    @Nullable
    private TranslationComponent statusText;
    @NotNull
    private OffsetDateTime startAt;
    @NotNull
    private OffsetDateTime finishedAt;
    @NotNull
    private BackgroundTaskStatus status = BackgroundTaskStatus.QUEUED;
    @NotNull
    private BackgroundTaskProgressBarType barType = BackgroundTaskProgressBarType.INDETERMINATE;
    private boolean disposalImmediatelyAfterComplete = false;
    private long max;
    private long current;

    private final @NotNull BackgroundTaskManager manager;

    public BackgroundTask(@NotNull BackgroundTaskManager manager){
        this.manager = manager;
    }

    @Override
    public void close() throws Exception {
        finishedAt = OffsetDateTime.now();
        if( status == BackgroundTaskStatus.QUEUED || status == BackgroundTaskStatus.RUNNING ){ // 只有这两个状态才变更为 COMPLETED，避免覆盖掉 FAILED 之类的状态
            status = BackgroundTaskStatus.COMPLETED;
        }
    }

    public double getProgress(){
        return max == 0 ? 0.0 : (double) current / (double) max;
    }
}
