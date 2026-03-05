package com.ghostchu.peerbanhelper.platform.mtr.exception;

/**
 * Base checked exception for all MTR (My Traceroute) errors.
 */
public class MtrException extends Exception {

    public MtrException(String message) {
        super(message);
    }

    public MtrException(String message, Throwable cause) {
        super(message, cause);
    }
}

