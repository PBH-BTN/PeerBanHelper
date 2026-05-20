package com.ghostchu.peerbanhelper.platform.mtr.exception;

/**
 * Thrown when the current platform or address family (IPv4/IPv6) is not
 * supported for ICMP tracing.
 */
public class MtrUnsupportedException extends MtrException {

    public MtrUnsupportedException(String message) {
        super(message);
    }

    public MtrUnsupportedException(String message, Throwable cause) {
        super(message, cause);
    }
}

