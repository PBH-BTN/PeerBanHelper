package hu.benzor.systemthemedetector.internal.detector.appearance;

import java.util.Optional;

import hu.benzor.systemthemedetector.api.theme.Theme.Appearance;
import hu.benzor.systemthemedetector.internal.command.CommandOutputLineMapper;
import hu.benzor.systemthemedetector.internal.command.FilteredCommandOutputLineMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class MacOsAppearanceDetector extends AppearanceDetector {

    private final CommandOutputLineMapper outputLineMapper;

    public MacOsAppearanceDetector() {
        ProcessBuilder pb = new ProcessBuilder(
            "defaults",
            "read",
            "-g",
            "AppleInterfaceStyle"
        );
        outputLineMapper = new FilteredCommandOutputLineMapper(pb, true);
    }

    @Override
    protected CommandOutputLineMapper commandOutputMapper() {
        return outputLineMapper;
    }

    @Override
    protected Optional<Appearance> outputLineToThemeMap(String line) {
        /*
         * Output is Dark if the appearance is dark. If light, then no stdout output and exit code is != 0.
         * This is represented by blank string
         */
        return switch (line.trim()) {
            case "Dark" -> {
                //log.debug("Appearance determined: {}", Appearance.DARK);
                yield Optional.of(Appearance.DARK);
            }
            case "" -> {
                //log.debug("Appearance determined: {}", Appearance.LIGHT);
                yield Optional.of(Appearance.LIGHT);
            }
            default -> {
                log.debug("Invalid line format: {}", line);
                yield Optional.empty();
            }
        };
    }

}
