package hu.benzor.systemthemedetector.api.listener;

import hu.benzor.systemthemedetector.api.theme.Theme;
import lombok.Getter;

import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

public class ListenerHandle<T extends Theme> {

    @Getter
    private final Class<T> type;
    private final Optional<ScheduledFuture<?>> task;

    public ListenerHandle(Class<T> type, ScheduledFuture<?> task) {
        this.type = type;
        this.task = Optional.ofNullable(task);
    }

    public static <T extends Theme> ListenerHandle<T> createEmpty(Class<T> type) {
        return new ListenerHandle<>(type, null);
    }

    public boolean isActive() {
        return task.map(x -> !x.isDone()).orElse(false);
    }

    public boolean isEmpty() {
        return task.isEmpty();
    }

    public void stop() {
        task.ifPresent(x -> x.cancel(true));
    }
}
