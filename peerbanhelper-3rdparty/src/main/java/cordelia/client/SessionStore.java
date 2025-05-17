package cordelia.client;

import java.util.concurrent.atomic.AtomicReference;

final class SessionStore {

    private final AtomicReference<Session> ref = new AtomicReference<>(null);

    void set(Session session) {
        ref.set(session);
    }

    boolean isEmpty() {
        return ref.get() == null || ref.get().id() == null || ref.get().id().isEmpty();
    }

    Session get() {
        return ref.get();
    }

}
