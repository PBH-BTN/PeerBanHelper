package com.ghostchu.peerbanhelper.util.backgroundtask;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import org.jetbrains.annotations.NotNull;

public interface BackgroundTask extends Runnable {

    BackgroundTask updateProgress(double progress);

    BackgroundTask updateIndeterminate(boolean indeterminate);

    double getProgress();

    TranslationComponent getMessage();

    TranslationComponent getTitle();

    boolean isIndeterminate();

    BackgroundTask setTitle(TranslationComponent title);

    BackgroundTask setMessage(TranslationComponent message);

    String getName();

    @NotNull BackgroundTaskStatus getTaskStatus();

    void setTaskStatus(@NotNull BackgroundTaskStatus taskStatus);
}
