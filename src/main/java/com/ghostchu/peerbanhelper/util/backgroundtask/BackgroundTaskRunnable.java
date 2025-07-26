package com.ghostchu.peerbanhelper.util.backgroundtask;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import org.slf4j.Logger;

public abstract class BackgroundTaskRunnable implements BackgroundTask {
    private final String name;
    private double progress = 0.0d;
    private boolean indeterminate = true;
    private TranslationComponent title;
    private TranslationComponent message;
    private Logger logger;

    public BackgroundTaskRunnable(String name) {
        this.name = name;
    }


    @Override
    public void updateProgress(double progress) {
        this.indeterminate = false;
        this.progress = progress;
    }

    @Override
    public void updateIndeterminate(boolean indeterminate) {
        this.indeterminate = indeterminate;
    }

    @Override
    public double getProgress() {
        return progress;
    }

    @Override
    public TranslationComponent getMessage() {
        return message;
    }

    @Override
    public TranslationComponent getTitle() {
        return title;
    }

    @Override
    public boolean isIndeterminate() {
        return indeterminate;
    }

    @Override
    public BackgroundTask setTitle(TranslationComponent title) {
        this.title = title;
        return this;
    }

    @Override
    public BackgroundTask setMessage(TranslationComponent message) {
        this.message = message;
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

}

