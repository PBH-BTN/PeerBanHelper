package hu.benzor.systemthemedetector.internal.detector;

import java.util.Optional;

import hu.benzor.systemthemedetector.api.environment.DesktopEnvironment;
import hu.benzor.systemthemedetector.api.environment.Platform;
import hu.benzor.systemthemedetector.api.theme.Theme.AccentColor;
import hu.benzor.systemthemedetector.api.theme.Theme.Appearance;
import hu.benzor.systemthemedetector.api.theme.Theme.Font;
import hu.benzor.systemthemedetector.internal.detector.accentcolor.LinuxAccentColorDetector;
import hu.benzor.systemthemedetector.internal.detector.accentcolor.MacOsAccentColorDetector;
import hu.benzor.systemthemedetector.internal.detector.accentcolor.WindowsAccentColorDetector;
import hu.benzor.systemthemedetector.internal.detector.appearance.LinuxAppearanceDetector;
import hu.benzor.systemthemedetector.internal.detector.appearance.MacOsAppearanceDetector;
import hu.benzor.systemthemedetector.internal.detector.appearance.WindowsAppearanceDetector;
import hu.benzor.systemthemedetector.internal.detector.font.LinuxFontDetector;
import hu.benzor.systemthemedetector.internal.detector.font.MacOsFontDetector;
import hu.benzor.systemthemedetector.internal.detector.font.WindowsFontDetector;
import hu.benzor.systemthemedetector.internal.environment.EnvironmentDetector;
import lombok.Getter;

public class DetectorFactory {

    @Getter
    private final Platform platform;
    @Getter
    private final DesktopEnvironment desktop;

    public DetectorFactory(EnvironmentDetector environmentDetector) {
        this.platform = environmentDetector.getPlatform();
        this.desktop = platform == Platform.LINUX ? environmentDetector.getDesktop() : DesktopEnvironment.UNKNOWN;
    }

    public Optional<ThemeDetector<AccentColor>> createAccentColorDetector() {
        return switch (platform) {
            case LINUX -> Optional.of(new LinuxAccentColorDetector());
            case MACOS -> Optional.of(new MacOsAccentColorDetector());
            case WINDOWS -> Optional.of(new WindowsAccentColorDetector());
            case UNKNOWN -> Optional.empty();
        };
    }

    public Optional<ThemeDetector<Appearance>> createAppearanceDetector() {
        return switch (platform) {
            case LINUX -> Optional.of(new LinuxAppearanceDetector());
            case MACOS -> Optional.of(new MacOsAppearanceDetector());
            case WINDOWS -> Optional.of(new WindowsAppearanceDetector());
            case UNKNOWN -> Optional.empty();
        };
    }

    public Optional<ThemeDetector<Font>> createFontDetector() {
        return switch (platform) {
            case LINUX -> Optional.of(new LinuxFontDetector(desktop));
            case MACOS -> Optional.of(new MacOsFontDetector());
            case WINDOWS -> Optional.of(new WindowsFontDetector());
            case UNKNOWN -> Optional.empty();
        };
    }
}
