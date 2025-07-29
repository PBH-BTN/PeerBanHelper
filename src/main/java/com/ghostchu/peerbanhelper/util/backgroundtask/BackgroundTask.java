package com.ghostchu.peerbanhelper.util.backgroundtask;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

public interface BackgroundTask extends Runnable {

    BackgroundTask updateProgress(double progress);

    BackgroundTask updateIndeterminate(boolean indeterminate);

    double getProgress();

    @Nullable
    TranslationComponent getMessage();

    TranslationComponent getTitle();

    boolean isIndeterminate();

    BackgroundTask setTitle(TranslationComponent title);

    BackgroundTask setMessage(TranslationComponent message);

    Collection<BackgroundTaskRunnable.Logger.LogEntry> getLogs();

    String getName();

    @NotNull BackgroundTaskStatus getTaskStatus();

    boolean isCancellable();

    void setStartAt(long startAt);

    long getStartAt();

    void setEndedAt(long endedAt);

    long getEndedAt();

    AtomicBoolean getRequestCancel();

    void setCancellable(boolean cancellable);

    void cancel();

    void setTaskStatus(@NotNull BackgroundTaskStatus taskStatus);

    String getId();
}
