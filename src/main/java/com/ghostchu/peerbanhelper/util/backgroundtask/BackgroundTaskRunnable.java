package com.ghostchu.peerbanhelper.util.backgroundtask;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.google.common.collect.EvictingQueue;
import org.jetbrains.annotations.NotNull;
import org.slf4j.event.Level;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BackgroundTaskRunnable implements BackgroundTask {
    private final String id = UUID.randomUUID().toString();
    private double progress = 0.0d;
    private boolean indeterminate = true;
    private TranslationComponent title;
    private TranslationComponent message;
    public Logger tlog = new Logger();
    private BackgroundTaskStatus taskStatus = BackgroundTaskStatus.NOT_STARTED;
    private boolean cancellable = false;
    private final AtomicBoolean requestCancel = new AtomicBoolean(false);
    private long startAt = 0;
    private long endedAt = 0;

    public BackgroundTaskRunnable(@NotNull TranslationComponent title) {
        this.title = title;
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

    @Override
    public EvictingQueue<Logger.LogEntry> getLogs() {
        return tlog.getLogs();
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

    @Override
    public boolean isCancellable() {
        return cancellable;
    }

    @Override
    public void setStartAt(long startAt) {
        this.startAt = startAt;
    }
    @Override
    public long getStartAt() {
        return startAt;
    }
    @Override
    public void setEndedAt(long endedAt) {
        this.endedAt = endedAt;
    }
    @Override
    public long getEndedAt() {
        return endedAt;
    }

    @Override
    public AtomicBoolean getRequestCancel() {
        return requestCancel;
    }

    @Override
    public void setCancellable(boolean cancellable) {
        this.cancellable = cancellable;
    }

    @Override
    public void cancel() {
        requestCancel.set(true);
    }
    @Override
    public String getId() {
        return id;
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

