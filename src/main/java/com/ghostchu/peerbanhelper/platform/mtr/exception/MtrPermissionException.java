package com.ghostchu.peerbanhelper.platform.mtr.exception;

/**
 * Thrown when the current process lacks the privileges needed to create a
 * raw/ICMP socket (errno EPERM / EACCES on POSIX, or equivalent on Windows).
 */
public class MtrPermissionException extends MtrException {

    /** Platform-specific error code (errno on POSIX, WSAGetLastError on Windows). */
    private final int errorCode;

    public MtrPermissionException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public MtrPermissionException(String message, int errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** @return platform error code that caused this exception */
    public int getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[errorCode=" + errorCode + "]: " + getMessage();
    }
}

