package hu.benzor.systemthemedetector;

import hu.benzor.systemthemedetector.api.environment.DesktopEnvironment;
import hu.benzor.systemthemedetector.api.environment.Platform;
import hu.benzor.systemthemedetector.api.listener.ListenerHandle;
import hu.benzor.systemthemedetector.api.theme.Theme;
import hu.benzor.systemthemedetector.api.theme.Theme.AccentColor;
import hu.benzor.systemthemedetector.api.theme.Theme.Appearance;
import hu.benzor.systemthemedetector.api.theme.Theme.Font;
import hu.benzor.systemthemedetector.internal.detector.DetectorFactory;
import hu.benzor.systemthemedetector.internal.detector.ThemeDetector;
import hu.benzor.systemthemedetector.internal.environment.EnvironmentDetector;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class SystemThemeDetector {

    private final List<ListenerHandle<? extends Theme>> listenerHandles = new CopyOnWriteArrayList<>();

    private final Platform platform;
    private final DesktopEnvironment desktop;

    private final Optional<ThemeDetector<AccentColor>> accentColorDetector;
    private final Optional<ThemeDetector<Appearance>> appearanceDetector;
    private final Optional<ThemeDetector<Font>> fontDetector;

    public SystemThemeDetector() {
        this(new DetectorFactory(new EnvironmentDetector()));
    }

    SystemThemeDetector(DetectorFactory detectorFactory) {
        this.platform = detectorFactory.getPlatform();
        this.desktop = detectorFactory.getDesktop();
        this.accentColorDetector = detectorFactory.createAccentColorDetector();
        this.appearanceDetector = detectorFactory.createAppearanceDetector();
        this.fontDetector = detectorFactory.createFontDetector();
    }

    public Optional<AccentColor> getAccentColor() {
        return accentColorDetector.flatMap(ThemeDetector::getTheme);
    }

    public Optional<Appearance> getAppearance() {
        return appearanceDetector.flatMap(ThemeDetector::getTheme);
    }

    public Optional<Font> getFont() {
        return fontDetector.flatMap(ThemeDetector::getTheme);
    }

    public ListenerHandle<AccentColor> onAccentColorChange(Consumer<AccentColor> callback) {
        removeInactiveHandles();
        ListenerHandle<AccentColor> handle = accentColorDetector
                .map(x -> x.registerCallback(callback))
                .orElseGet(() -> ListenerHandle.createEmpty(AccentColor.class));
        listenerHandles.add(handle);
        return handle;
    }

    public ListenerHandle<Appearance> onAppearanceChange(Consumer<Appearance> callback) {
        removeInactiveHandles();
        ListenerHandle<Appearance> handle = appearanceDetector
                .map(x -> x.registerCallback(callback))
                .orElseGet(() -> ListenerHandle.createEmpty(Appearance.class));
        listenerHandles.add(handle);
        return handle;
    }

    public ListenerHandle<Font> onFontChange(Consumer<Font> callback) {
        removeInactiveHandles();
        ListenerHandle<Font> handle = fontDetector
                .map(x -> x.registerCallback(callback))
                .orElseGet(() -> ListenerHandle.createEmpty(Font.class));
        listenerHandles.add(handle);
        return handle;
    }

    public Platform getPlatform() {
        return platform;
    }

    public DesktopEnvironment getDesktop() {
        return desktop;
    }

    public void stopAllListeners(Class<? extends Theme> type) {
        listenerHandles.removeIf(
                handle -> {
                    if (type.isAssignableFrom(handle.getType())) {
                        handle.stop();
                        return true;
                    }
                    return false;
                });
    }

    public void stopAllListeners() {
        stopAllListeners(Theme.class);
    }

    List<ListenerHandle<? extends Theme>> inspectHandles() {
        return listenerHandles.stream().toList();
    }

    private void removeInactiveHandles() {
        listenerHandles.removeIf(handle -> !handle.isActive());
    }
}