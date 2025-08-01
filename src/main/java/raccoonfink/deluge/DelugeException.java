package raccoonfink.deluge;

import java.io.Serial;

public final class DelugeException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    public DelugeException() {
    }

    public DelugeException(final String message) {
        super(message);
    }

    public DelugeException(final Throwable cause) {
        super(cause);
    }

    public DelugeException(final String message, final Throwable cause) {
        super(message, cause);
    }

}
