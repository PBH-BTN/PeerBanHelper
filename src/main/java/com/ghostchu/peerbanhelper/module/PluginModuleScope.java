package com.ghostchu.peerbanhelper.module;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** One activation of a plugin. Queued scans retain this scope even if the same JAR is restarted. */
public final class PluginModuleScope {
    private final ReentrantReadWriteLock calls = new ReentrantReadWriteLock(true);
    private volatile boolean active;
    private volatile boolean closing;

    public void activate() {
        active = true;
    }

    boolean isActive() {
        return active && !closing;
    }

    boolean isClosing() {
        return closing;
    }

    void invoke(Runnable call) {
        if (!isActive()) return;
        calls.readLock().lock();
        try {
            if (isActive()) call.run();
        } finally {
            calls.readLock().unlock();
        }
    }

    public boolean drain(long timeout, TimeUnit unit) throws InterruptedException {
        closing = true;
        if (!calls.writeLock().tryLock(timeout, unit)) return false;
        calls.writeLock().unlock();
        return true;
    }
}
