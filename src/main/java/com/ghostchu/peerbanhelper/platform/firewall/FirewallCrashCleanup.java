package com.ghostchu.peerbanhelper.platform.firewall;

/**
 * Describes how firewall state is reclaimed after abnormal process termination.
 */
public enum FirewallCrashCleanup {
    /**
     * The kernel ties the firewall object to the process owner and removes it
     * when the owning socket or session closes.
     */
    PROCESS_OWNED,
    /**
     * The OS owns the firewall session and removes its objects when the process exits.
     */
    DYNAMIC_SESSION,
    /**
     * The next PeerBanHelper start finds and removes state owned by dead processes.
     */
    NEXT_START_SWEEP,
    /**
     * The backend cannot provide a reliable crash-cleanup guarantee.
     */
    NONE
}
