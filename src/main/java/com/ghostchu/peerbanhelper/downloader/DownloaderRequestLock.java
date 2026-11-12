package com.ghostchu.peerbanhelper.downloader;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Coordinates poller reads with exclusive downloader state changes such as ban-list updates.
 */
public final class DownloaderRequestLock {
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

    public <T> T withReadLock(@NotNull Downloader downloader, @NotNull Supplier<T> action) {
        ReentrantReadWriteLock lock = lockFor(downloader);
        lock.readLock().lock();
        try {
            return action.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    public <T> T withWriteLock(@NotNull Downloader downloader, @NotNull Supplier<T> action) {
        ReentrantReadWriteLock lock = lockFor(downloader);
        lock.writeLock().lock();
        try {
            return action.get();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private @NotNull ReentrantReadWriteLock lockFor(@NotNull Downloader downloader) {
        return locks.computeIfAbsent(downloader.getId(), ignored -> new ReentrantReadWriteLock());
    }
}
