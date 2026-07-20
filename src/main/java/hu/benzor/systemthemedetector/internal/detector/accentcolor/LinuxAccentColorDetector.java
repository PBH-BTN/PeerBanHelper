package hu.benzor.systemthemedetector.internal.detector.accentcolor;

import hu.benzor.systemthemedetector.api.theme.Theme.AccentColor;
import hu.benzor.systemthemedetector.internal.command.CommandOutputLineMapper;
import hu.benzor.systemthemedetector.internal.command.FilteredCommandOutputLineMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Slf4j
public final class LinuxAccentColorDetector extends AccentColorDetector {

    private static final Pattern cmdOutputPattern = Pattern.compile(
            "\\(<\\((\\d+(?:\\.\\d+)?), (\\d+(?:\\.\\d+)?), (\\d+(?:\\.\\d+)?)\\)>,\\)"
    );

    private final CommandOutputLineMapper outputLineMapper;


    public LinuxAccentColorDetector() {
        ProcessBuilder pb = new ProcessBuilder(
                "gdbus",
                "call",
                "--session",
                "--timeout=1000",
                "--dest=org.freedesktop.portal.Desktop",
                "--object-path=/org/freedesktop/portal/desktop",
                "--method=org.freedesktop.portal.Settings.ReadOne",
                "org.freedesktop.appearance",
                "accent-color"
        );
        outputLineMapper = new FilteredCommandOutputLineMapper(pb);
    }

    @Override
    protected CommandOutputLineMapper commandOutputMapper() {
        return outputLineMapper;
    }

    @Override
    protected Optional<AccentColor> outputLineToThemeMap(String line) {
        /*
         * We expect strings of the form "(<(d, d, d)>,)" where each d is a decimal number,
         * and we construct the color from these.
         */
        Matcher matcher = cmdOutputPattern.matcher(line);

        if (!matcher.matches()) {
            log.debug("Invalid line format: {}", line);
            return Optional.empty();
        }
        try {
            int[] rgbColors = IntStream.of(1, 2, 3)
                    .mapToObj(matcher::group)
                    .mapToDouble(Double::parseDouble)
                    .mapToInt(AccentColorDetector::decimalToIntRgb)
                    .toArray();
            AccentColor accentColor = AccentColor.fromArray(rgbColors);
            log.debug("Accent color determined: {}", accentColor);
            return Optional.of(accentColor);

        } catch (IllegalArgumentException e) {
            log.debug("Members in the color string tuple are invalid: {}", line);
            return Optional.empty();
        }
    }
}
