package com.ghostchu.peerbanhelper.platform.firewall;

/**
 * Raised when a firewall operation is requested but the backend is unavailable.
 */
public final class FirewallUnavailableException extends FirewallException {
    public FirewallUnavailableException(String message) {
        super(message);
    }
}
