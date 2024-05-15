package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class WatchDog {
    private final String name;
    private final long timeout;
    private final AtomicLong lastFeedAt = new AtomicLong(System.currentTimeMillis());
    private final ScheduledExecutorService service;
    private final Runnable hungry;
    private final Runnable good;

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

    public void feed() {
        lastFeedAt.set(System.currentTimeMillis());
    }

    private void watchDogCheck() {
        try {
            CompletableFuture.runAsync(() -> {
                if (System.currentTimeMillis() - lastFeedAt.get() > timeout) {
                    hungry();
                } else {
                    good();
                }
            }).get(3, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error(Lang.WATCH_DOG_CALLBACK_BLOCKED, e);
        }

    }

    private void good() {
        if (good != null) {
            good.run();
        }
    }

    private void hungry() {
        log.info(Lang.WATCH_DOG_HUNGRY, name, timeout + "ms");
        if (hungry != null) {
            hungry.run();
        }
    }
}
