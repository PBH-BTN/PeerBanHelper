package com.ghostchu.peerbanhelper.platform.firewall;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable feature snapshot for a firewall backend.
 */
public record FirewallCapabilities(
        @NotNull FirewallBackend backend,
        boolean available,
        boolean ipv4,
        boolean ipv6,
        boolean inbound,
        boolean outbound,
        boolean systemWide,
        boolean processFiltering,
        boolean requiresElevation,
        @NotNull FirewallCrashCleanup crashCleanup,
        @NotNull String detail
) {
    public FirewallCapabilities {
        if (detail.isBlank()) {
            throw new IllegalArgumentException("detail must not be blank");
        }
    }
}
