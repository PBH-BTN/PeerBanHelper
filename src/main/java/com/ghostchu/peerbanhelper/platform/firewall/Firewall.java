package com.ghostchu.peerbanhelper.platform.firewall;

import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Set;

/**
 * Lifecycle-scoped native firewall address list.
 *
 * <p>All mutation methods are transactional from the caller's perspective.
 * A successful call commits the complete requested change; a failed call leaves
 * the previously committed address set unchanged.</p>
 */
public interface Firewall extends AutoCloseable {
    /**
     * Returns the immutable feature snapshot for this backend.
     */
    @NotNull FirewallCapabilities capabilities();

    /**
     * Returns an immutable snapshot of the committed address list.
     */
    @NotNull Set<InetAddress> addresses();

    /**
     * Adds addresses to both inbound and outbound blocking.
     */
    void add(@NotNull Collection<? extends InetAddress> addresses);

    /**
     * Removes addresses from both inbound and outbound blocking.
     */
    void remove(@NotNull Collection<? extends InetAddress> addresses);

    /**
     * Atomically replaces the complete managed address list.
     */
    void replace(@NotNull Collection<? extends InetAddress> addresses);

    /**
     * Removes every managed address while keeping the backend active.
     */
    void clear();

    /**
     * Removes all native state owned by this process. Calls after the first are no-ops.
     */
    @Override
    void close();
}
