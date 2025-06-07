package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.text.Lang;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    @Setter
    @Getter
    private String lastOperation = "N/A";

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
            }, executor).get(3, TimeUnit.SECONDS);
        } catch (Throwable e) {
            log.error(tlUI(Lang.WATCH_DOG_CALLBACK_BLOCKED), e);
        }

    }

    private void good() {
        if (good != null) {
            good.run();
        }
    }

    private void hungry() {
        log.info(tlUI(Lang.WATCH_DOG_HUNGRY, name, timeout + "ms", lastOperation));
        if (hungry != null) {
            hungry.run();
        }
    }
}
