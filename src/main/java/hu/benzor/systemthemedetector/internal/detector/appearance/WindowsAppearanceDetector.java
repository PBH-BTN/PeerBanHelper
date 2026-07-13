package hu.benzor.systemthemedetector.internal.detector.appearance;

import hu.benzor.systemthemedetector.api.theme.Theme.Appearance;
import hu.benzor.systemthemedetector.internal.command.CommandOutputLineMapper;
import hu.benzor.systemthemedetector.internal.command.FilteredCommandOutputLineMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public final class WindowsAppearanceDetector extends AppearanceDetector {

    private final CommandOutputLineMapper outputLineMapper;

    public WindowsAppearanceDetector() {
        ProcessBuilder pb = new ProcessBuilder(
                "reg",
                "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v",
                "AppsUseLightTheme"
        );
        outputLineMapper = new FilteredCommandOutputLineMapper(pb, "AppsUseLightTheme");
    }

    @Override
    protected CommandOutputLineMapper commandOutputMapper() {
        return outputLineMapper;
    }

    @Override
    protected Optional<Appearance> outputLineToThemeMap(String line) {
        /*
         * Output is of the form
         *   AppsUseLightTheme    REG_DWORD    0x1
         * 0 = dark
         * 1 = light
         */
        String[] parts = line.split("REG_DWORD");
        if (parts.length != 2) {
            log.debug("Invalid line format: {}", line);
            return Optional.empty();
        }
        String appearanceId = parts[1].trim();
        try {
            int appearanceNumber = Integer.decode(appearanceId);
            return switch (appearanceNumber) {
                case 0 -> {
                    //log.debug("Appearance determined: {}", Appearance.DARK);
                    yield Optional.of(Appearance.DARK);
                }
                case 1 -> {
                    //log.debug("Appearance determined: {}", Appearance.LIGHT);
                    yield Optional.of(Appearance.LIGHT);
                }
                default -> {
                    log.debug("Could not determine appearance from number: {}", appearanceNumber);
                    yield Optional.empty();
                }
            };
        } catch (NumberFormatException e) {
            log.debug("Invalid number format: {}", appearanceId);
            return Optional.empty();
        }
    }

}
