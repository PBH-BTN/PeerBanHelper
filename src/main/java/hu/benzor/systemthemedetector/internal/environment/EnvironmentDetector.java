package hu.benzor.systemthemedetector.internal.environment;

import hu.benzor.systemthemedetector.api.environment.DesktopEnvironment;
import hu.benzor.systemthemedetector.api.environment.Platform;
import hu.benzor.systemthemedetector.internal.environment.propertyreader.EnvironmentReader;
import hu.benzor.systemthemedetector.internal.environment.propertyreader.PropertyReader;
import hu.benzor.systemthemedetector.internal.environment.propertyreader.SystemPropertyReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class EnvironmentDetector {

    private final PropertyReader systemPropertyReader;
    private final PropertyReader environmentVariableReader;
    
    public EnvironmentDetector() {
        this(new SystemPropertyReader(), new EnvironmentReader());
    }

    public Platform getPlatform() {
        return systemPropertyReader.getValue("os.name")
            .map(
                value -> {
                    log.debug("Raw platform string: {}", value);
                    return value.toLowerCase();
                }
            )
            .map(
                osName -> switch (osName) {
                    case String s when s.contains("win") -> {
                        log.debug("Platform identified as Windows.");
                        yield Platform.WINDOWS;
                    }
                    case String s when s.contains("mac") -> {
                        log.debug("Platform identified as MacOS.");
                        yield Platform.MACOS;
                    }
                    case String s when s.contains("nux") -> {
                        log.debug("Platform identified as Linux.");
                        yield Platform.LINUX;
                    }
                    default -> {
                        log.debug("Could not identify operating system: {}", osName);
                        yield Platform.UNKNOWN;
                    }
                }
            ).orElseGet(
                () -> {
                    log.debug("Platform cannot be determined as its descriptor was null.");
                    return Platform.UNKNOWN;
                }
            );
    }

    public DesktopEnvironment getDesktop() {
        return environmentVariableReader.getValue("XDG_CURRENT_DESKTOP")
            .map(
                value -> {
                    log.debug("Raw desktop environment string: {}", value);
                    return value.toLowerCase();
                }
            )
            .map(
                deName -> switch (deName) {
                    case String s when s.contains("gnome") -> {
                        log.debug("Desktop environment identified as Gnome.");
                        yield DesktopEnvironment.GNOME;
                    }
                    case String s when s.contains("kde") -> {
                        log.debug("Desktop environment identified as KDE.");
                        yield DesktopEnvironment.KDE;
                    }
                    case String s when s.contains("xfce") -> {
                        log.debug("Desktop environment identified as XFCE.");
                        yield DesktopEnvironment.XFCE;
                    }
                    case String s when s.contains("cinnamon") -> {
                        log.debug("Desktop environment identified as Cinnamon.");
                        yield DesktopEnvironment.CINNAMON;
                    }
                    case String s when s.contains("mate") -> {
                        log.debug("Desktop environment identified as MATE.");
                        yield DesktopEnvironment.MATE;
                    }
                    default -> {
                        log.debug("Could not identify desktop environment: {}", deName);
                        yield DesktopEnvironment.UNKNOWN;
                    }
                }
            )
            .orElseGet(
                () -> {
                    log.debug("Desktop environment cannot be determined as its descriptor was null.");
                    return DesktopEnvironment.UNKNOWN;
                }
            );
    }
}
