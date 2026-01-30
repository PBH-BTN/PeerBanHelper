package cordelia.client;

public class TransmissionIOException extends RuntimeException {
    public TransmissionIOException(String message) {
        super(message);
    }

    public TransmissionIOException(String message, Throwable cause) {
        super(message, cause);
    }
}
