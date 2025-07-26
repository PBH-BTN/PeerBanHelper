package com.ghostchu.peerbanhelper.util.backgroundtask;

import com.ghostchu.peerbanhelper.text.TranslationComponent;

public interface BackgroundTask extends Runnable{

    void updateProgress(double progress);

    void updateIndeterminate(boolean indeterminate);

    double getProgress();

    TranslationComponent getMessage();

    TranslationComponent getTitle();

    boolean isIndeterminate();

    BackgroundTask setTitle(TranslationComponent title);

    BackgroundTask setMessage(TranslationComponent message);

    String getName();
}
