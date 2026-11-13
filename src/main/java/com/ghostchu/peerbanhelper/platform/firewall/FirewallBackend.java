package com.ghostchu.peerbanhelper.platform.firewall;

/**
 * Native firewall implementation selected for the current platform.
 */
public enum FirewallBackend {
    NFTABLES,
    WINDOWS_FILTERING_PLATFORM,
    PACKET_FILTER,
    UNSUPPORTED
}
