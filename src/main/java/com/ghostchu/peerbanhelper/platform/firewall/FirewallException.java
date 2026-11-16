package com.ghostchu.peerbanhelper.platform.firewall;

/**
 * Base exception for native firewall failures.
 */
public class FirewallException extends RuntimeException {
    public FirewallException(String message) {
        super(message);
    }

    public FirewallException(String message, Throwable cause) {
        super(message, cause);
    }
}
