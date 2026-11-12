package com.ghostchu.peerbanhelper.downloader;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Serializes downloader state changes such as login and ban-list updates.
 */
public final class DownloaderRequestLock {
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withLock(@NotNull Downloader downloader, @NotNull Supplier<T> action) {
        ReentrantLock lock = lockFor(downloader);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private @NotNull ReentrantLock lockFor(@NotNull Downloader downloader) {
        return locks.computeIfAbsent(downloader.getId(), ignored -> new ReentrantLock());
    }
}
