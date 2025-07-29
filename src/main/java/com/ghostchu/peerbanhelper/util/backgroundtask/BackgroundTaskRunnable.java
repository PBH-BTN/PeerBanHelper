package com.ghostchu.peerbanhelper.util.backgroundtask;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.google.common.collect.EvictingQueue;
import org.jetbrains.annotations.NotNull;
import org.slf4j.event.Level;

public abstract class BackgroundTaskRunnable implements BackgroundTask {
    private final String name;
    private double progress = 0.0d;
    private boolean indeterminate = true;
    private TranslationComponent title;
    private TranslationComponent message;
    public Logger log = new Logger();
    private BackgroundTaskStatus taskStatus = BackgroundTaskStatus.NOT_STARTED;

    public BackgroundTaskRunnable(String name) {
        this.name = name;
    }

    @Override
    public BackgroundTask updateProgress(double progress) {
        this.indeterminate = false;
        this.progress = progress;
        return this;
    }

    @Override
    public BackgroundTask updateIndeterminate(boolean indeterminate) {
        this.indeterminate = indeterminate;
        return this;
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

    public Logger getLog() {
        return log;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public @NotNull BackgroundTaskStatus getTaskStatus() {
        return taskStatus;
    }

    @Override
    public void setTaskStatus(@NotNull BackgroundTaskStatus taskStatus) {
        this.taskStatus = taskStatus;
    }

    public static class Logger {
        EvictingQueue<LogEntry> logs = EvictingQueue.create(300);

        public void info(String message) {
            logs.add(new LogEntry(System.currentTimeMillis(), Level.INFO, Thread.currentThread().getName(), message));
        }

        public void warn(String message) {
            logs.add(new LogEntry(System.currentTimeMillis(), Level.WARN, Thread.currentThread().getName(), message));
        }

        public void error(String message) {
            logs.add(new LogEntry(System.currentTimeMillis(), Level.ERROR, Thread.currentThread().getName(), message));
        }

        public void debug(String message) {
            logs.add(new LogEntry(System.currentTimeMillis(), Level.DEBUG, Thread.currentThread().getName(), message));
        }

        public EvictingQueue<LogEntry> getLogs() {
            return logs;
        }

        public record LogEntry(long time, Level level, String threadName, String text) {
        }
    }

}

