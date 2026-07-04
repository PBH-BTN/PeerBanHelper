package com.ghostchu.peerbanhelper.platform.impl.posix.mtr;

/**
 * Describes which mode was successfully used to open an ICMP socket.
 */
public enum PosixSocketMode {
    /**
     * {@code SOCK_DGRAM + IPPROTO_ICMP} – unprivileged mode.
     * Works on Linux when {@code net.ipv4.ping_group_range} allows the current
     * user, and on macOS without any special configuration.
     */
    DGRAM_UNPRIVILEGED,

    /**
     * {@code SOCK_RAW + IPPROTO_ICMP} – requires root or {@code CAP_NET_RAW}.
     */
    RAW_PRIVILEGED
}

