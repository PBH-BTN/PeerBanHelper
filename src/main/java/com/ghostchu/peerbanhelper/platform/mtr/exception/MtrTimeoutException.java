package com.ghostchu.peerbanhelper.platform.mtr.exception;

/**
 * Thrown when the overall MTR trace exceeds {@code MtrOptions.totalTimeout}.
 */
public class MtrTimeoutException extends MtrException {

    public MtrTimeoutException(String message) {
        super(message);
    }

    public MtrTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}

