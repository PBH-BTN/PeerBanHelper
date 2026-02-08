package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.text.Lang;
import io.sentry.*;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.protocol.SentryThread;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class WatchDog implements AutoCloseable {
    private final String name;
    private final long timeout;
    private final AtomicLong lastFeedAt = new AtomicLong(System.currentTimeMillis());
    private final ScheduledExecutorService service;
    private final Runnable hungry;
    private final Runnable good;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(); // Watch dog 使用平台线程
    @Getter
    private String lastOperation = "N/A";
    @Getter
    private boolean isDownloaderIO;

    public WatchDog(String name, long timeout, @NotNull Runnable hungry, @Nullable Runnable good) {
        this.name = name;
        this.timeout = timeout;
        this.hungry = hungry;
        this.good = good;
        this.service = Executors.newScheduledThreadPool(1, r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("PBH-Watchdog-" + name);
            return thread;
        });
    }

    public void start() {
        this.service.scheduleAtFixedRate(this::watchDogCheck, 1, timeout, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        this.service.shutdown();
    }

    public void feed() {
        lastFeedAt.set(System.currentTimeMillis());
    }

    private void watchDogCheck() {
        try {
            CompletableFuture.runAsync(() -> {
                if ((System.currentTimeMillis() - lastFeedAt.get()) > timeout) {
                    hungry();
                } else {
                    good();
                }
            }, executor).get(10, TimeUnit.SECONDS);
        } catch (Throwable e) {
            log.error(tlUI(Lang.WATCH_DOG_CALLBACK_BLOCKED), e);
            Sentry.captureException(e);
        }
    }

    private void good() {
        if (good != null) {
            good.run();
        }
    }

    private void hungry() {
        if (isDownloaderIO) {
            log.warn(tlUI(Lang.WATCH_DOG_HUNGRY_IN_DOWNLOADER_IO, name, timeout + "ms", lastOperation));
        } else {
            log.warn(tlUI(Lang.WATCH_DOG_HUNGRY, name, timeout + "ms", lastOperation));
        }
        Message message = new Message();
        message.setMessage("Detected dead-lock or long-time running thread, watchdog triggered.");
        SentryEvent event = new SentryEvent();
        event.setLevel(SentryLevel.WARNING);
        event.setMessage(message);
        event.setTag("watchdog.name", name);
        event.setTag("watchdog.timeout", String.valueOf(timeout));
        event.setTag("watchdog.last_operation", lastOperation);
        event.setTag("watchdog.in_downloader_io", String.valueOf(isDownloaderIO));
        event.setThreads(SentryUtils.getSentryThreads());
        Sentry.captureEvent(event);
        if (hungry != null) {
            hungry.run();
        }
    }

    public void setLastOperation(String lastOperation, boolean isDownloaderIO) {
        this.lastOperation = lastOperation;
        this.isDownloaderIO = isDownloaderIO;
    }
}
