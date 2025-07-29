package com.ghostchu.peerbanhelper.util.backgroundtask;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.google.common.collect.EvictingQueue;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

public interface BackgroundTask extends Runnable {

    BackgroundTask updateProgress(double progress);

    BackgroundTask updateIndeterminate(boolean indeterminate);

    double getProgress();

    TranslationComponent getMessage();

    TranslationComponent getTitle();

    boolean isIndeterminate();

    BackgroundTask setTitle(TranslationComponent title);

    BackgroundTask setMessage(TranslationComponent message);

    EvictingQueue<BackgroundTaskRunnable.Logger.LogEntry> getLogs();

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
}
