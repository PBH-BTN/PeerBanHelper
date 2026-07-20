package hu.benzor.systemthemedetector.internal.detector;

import hu.benzor.systemthemedetector.api.listener.ListenerHandle;
import hu.benzor.systemthemedetector.api.theme.Theme;
import hu.benzor.systemthemedetector.internal.command.CommandOutputLineMapper;
import hu.benzor.systemthemedetector.internal.listener.ThemeChangeListener;
import hu.benzor.systemthemedetector.internal.scheduler.Scheduler;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
public abstract class ThemeDetector<T extends Theme> {

    public final Optional<T> getTheme() {
        Optional<T> theme = getThemeInternal();
        log.debug("Current {} is {}.", type().getSimpleName(), theme.map(T::toString).orElse("unknown"));
        return theme;
    }

    public final ListenerHandle<T> registerCallback(Consumer<T> callback) {
        ThemeChangeListener<T> listener = ThemeChangeListener
                .builder(type())
                .themeSupplier(this::getThemeInternal)
                .callback(callback)
                .build();
        return new ListenerHandle<>(type(), Scheduler.schedule(listener));
    }

    protected abstract CommandOutputLineMapper commandOutputMapper();

    protected abstract Optional<T> outputLineToThemeMap(String line);

    protected abstract Class<T> type();

    private Optional<T> getThemeInternal() {
        return commandOutputMapper().mapLine(this::outputLineToThemeMap);
    }
}
