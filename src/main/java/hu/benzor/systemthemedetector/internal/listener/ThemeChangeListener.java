package hu.benzor.systemthemedetector.internal.listener;

import hu.benzor.systemthemedetector.api.theme.Theme;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public class ThemeChangeListener<T extends Theme> implements Runnable {

    private final Class<T> type;
    private final Supplier<Optional<T>> themeSupplier;
    private final Consumer<T> callback;

    private volatile T previousTheme;

    private ThemeChangeListener(Class<T> type, Supplier<Optional<T>> themeSupplier, Consumer<T> callback) {
        this.type = type;
        this.themeSupplier = themeSupplier;
        this.callback = callback;

        previousTheme = themeSupplier.get().orElse(null);
        Optional.ofNullable(previousTheme).ifPresent(callback);
    }

    @Override
    public void run() {
        Optional<T> currentTheme = themeSupplier.get();
        currentTheme
                .filter(curr -> !curr.equals(previousTheme))
                .ifPresent(
                        curr -> {
                            callback.accept(curr);
                            previousTheme = curr;
                        }
                );
    }

    public static <T extends Theme> ThemeChangeListenerBuilder<T> builder(Class<T> type) {
        return new ThemeChangeListenerBuilder<>(type);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ThemeChangeListenerBuilder<T extends Theme> {
        private final Class<T> type;
        private Supplier<Optional<T>> themeSupplier;
        private Consumer<T> callback;

        public ThemeChangeListenerBuilder<T> themeSupplier(Supplier<Optional<T>> themeSupplier) {
            this.themeSupplier = themeSupplier;
            return this;
        }

        public ThemeChangeListenerBuilder<T> callback(Consumer<T> callback) {
            this.callback = callback;
            return this;
        }

        public ThemeChangeListener<T> build() {
            Objects.requireNonNull(themeSupplier, "themeSupplier must not be null.");
            Objects.requireNonNull(callback, "callback must not be null.");
            return new ThemeChangeListener<T>(type, themeSupplier, callback);
        }
    }

}
