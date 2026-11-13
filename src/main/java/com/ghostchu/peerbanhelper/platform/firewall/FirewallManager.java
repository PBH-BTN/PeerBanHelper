package com.ghostchu.peerbanhelper.platform.firewall;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the shutdown hook for an active native firewall backend.
 */
@Slf4j
public final class FirewallManager implements Firewall {
    private final Firewall delegate;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread shutdownHook;

    public FirewallManager(@NotNull Firewall delegate) {
        if (!delegate.capabilities().available()) {
            throw new IllegalArgumentException("FirewallManager requires an available firewall");
        }
        this.delegate = delegate;
        this.shutdownHook = Thread.ofPlatform()
                .name("PeerBanHelper-Firewall-Cleanup")
                .unstarted(this::closeFromShutdownHook);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    @Override
    public @NotNull FirewallCapabilities capabilities() {
        return delegate.capabilities();
    }

    @Override
    public @NotNull Set<InetAddress> addresses() {
        requireOpen();
        return delegate.addresses();
    }

    @Override
    public void add(@NotNull Collection<? extends InetAddress> addresses) {
        requireOpen();
        delegate.add(addresses);
    }

    @Override
    public void remove(@NotNull Collection<? extends InetAddress> addresses) {
        requireOpen();
        delegate.remove(addresses);
    }

    @Override
    public void replace(@NotNull Collection<? extends InetAddress> addresses) {
        requireOpen();
        delegate.replace(addresses);
    }

    @Override
    public void clear() {
        requireOpen();
        delegate.clear();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            delegate.close();
        } finally {
            removeShutdownHookQuietly();
        }
    }

    private void closeFromShutdownHook() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            delegate.close();
        } catch (Throwable throwable) {
            log.warn("Unable to clean up the native firewall during JVM shutdown", throwable);
        }
    }

    private void removeShutdownHookQuietly() {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // The JVM is already shutting down and the hook will run or has run.
        }
    }

    private void requireOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Firewall is closed");
        }
    }
}
