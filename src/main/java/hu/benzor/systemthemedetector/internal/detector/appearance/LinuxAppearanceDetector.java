package hu.benzor.systemthemedetector.internal.detector.appearance;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hu.benzor.systemthemedetector.api.theme.Theme.Appearance;
import hu.benzor.systemthemedetector.internal.command.CommandOutputLineMapper;
import hu.benzor.systemthemedetector.internal.command.FilteredCommandOutputLineMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class LinuxAppearanceDetector extends AppearanceDetector {

    private static final Pattern cmdOutputPattern = Pattern.compile("\\(<uint32 (\\d+)>,\\)");

    private final CommandOutputLineMapper outputLineMapper;

    public LinuxAppearanceDetector() {
        ProcessBuilder pb = new ProcessBuilder(
            "gdbus",
            "call",
            "--session",
            "--timeout=1000",
            "--dest=org.freedesktop.portal.Desktop",
            "--object-path=/org/freedesktop/portal/desktop",
            "--method=org.freedesktop.portal.Settings.ReadOne",
            "org.freedesktop.appearance",
            "color-scheme"
        );
        outputLineMapper = new FilteredCommandOutputLineMapper(pb);
    }

    @Override
    protected CommandOutputLineMapper commandOutputMapper() {
        return outputLineMapper;
    }

    @Override
    protected Optional<Appearance> outputLineToThemeMap(String line) {
        /*
         * We expect strings of the form "(<uint32 n>,)" where n is an unsigned integer,
         * and we want to extract this integer.
         * 0 = no preference
         * 1 = dark
         * 2 = light
         */
        Matcher matcher = cmdOutputPattern.matcher(line);

        if (!matcher.matches()) {
            log.debug("Invalid line format: {}", line);
            return Optional.empty();
        }

        String appearanceString = matcher.group(1);
        try {
            int appearanceNumber = Integer.parseInt(appearanceString);
            Optional<Appearance> appearance = switch (appearanceNumber) {
                case 1 -> {
                    //log.debug("Appearance determined: {}", Appearance.DARK);
                    yield Optional.of(Appearance.DARK);
                }
                case 2 -> {
                    //log.debug("Appearance determined: {}", Appearance.LIGHT);
                    yield Optional.of(Appearance.LIGHT);
                }
                case 0 -> {
                    //log.debug("Appearance is up to each app to determine");
                    yield Optional.of(Appearance.NO_PREFERENCE);
                }
                default -> {
                    log.debug("Could not determine appearance from number: {}", appearanceNumber);
                    yield Optional.empty();
                }
            };
            return appearance;

        } catch (NumberFormatException e) {
            log.debug("Invalid appearance string received: {}", appearanceString);
            return Optional.empty();
        }
    }

}
