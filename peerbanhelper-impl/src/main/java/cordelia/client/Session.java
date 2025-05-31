package cordelia.client;


public final class Session {

    public static final String SESSION_ID = "X-Transmission-Session-Id";

    private final String id;

    public Session() {
        this("");
    }

    public Session(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

}
