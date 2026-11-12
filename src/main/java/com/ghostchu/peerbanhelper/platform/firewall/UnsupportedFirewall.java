package com.ghostchu.peerbanhelper.platform.firewall;

import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Set;

/**
 * Firewall implementation used when no usable native backend is available.
 */
public final class UnsupportedFirewall implements Firewall {
    private final FirewallCapabilities capabilities;

    public UnsupportedFirewall(@NotNull FirewallBackend backend, @NotNull String detail) {
        this.capabilities = new FirewallCapabilities(
                backend,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                FirewallCrashCleanup.NONE,
                detail
        );
    }

    public UnsupportedFirewall(@NotNull String detail) {
        this(FirewallBackend.UNSUPPORTED, detail);
    }

    @Override
    public @NotNull FirewallCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public @NotNull Set<InetAddress> addresses() {
        return Set.of();
    }

    @Override
    public void add(@NotNull Collection<? extends InetAddress> addresses) {
        throw unavailable();
    }

    @Override
    public void remove(@NotNull Collection<? extends InetAddress> addresses) {
        throw unavailable();
    }

    @Override
    public void replace(@NotNull Collection<? extends InetAddress> addresses) {
        throw unavailable();
    }

    @Override
    public void clear() {
        throw unavailable();
    }

    @Override
    public void close() {
        // Nothing to release.
    }

    private FirewallUnavailableException unavailable() {
        return new FirewallUnavailableException(capabilities.detail());
    }
}
