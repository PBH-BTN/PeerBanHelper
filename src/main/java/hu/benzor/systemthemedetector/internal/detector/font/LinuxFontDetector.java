package hu.benzor.systemthemedetector.internal.detector.font;

import hu.benzor.systemthemedetector.api.environment.DesktopEnvironment;
import hu.benzor.systemthemedetector.api.theme.Theme.Font;
import hu.benzor.systemthemedetector.internal.command.CommandOutputLineMapper;
import hu.benzor.systemthemedetector.internal.command.FilteredCommandOutputLineMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class LinuxFontDetector extends FontDetector {

    private static final Pattern cmdOutputPattern = Pattern.compile(
            "^'(.+?)(?:,?\\s+)(\\d+(?:\\.\\d+)?)'$"
    );

    private final CommandOutputLineMapper outputLineMapper;

    public LinuxFontDetector(DesktopEnvironment desktop) {
        ProcessBuilder pb = new ProcessBuilder(
                "gsettings",
                "get",
                getDconfInterfaceSchema(desktop),
                "font-name"
        );
        this.outputLineMapper = new FilteredCommandOutputLineMapper(pb);
    }

    @Override
    protected CommandOutputLineMapper commandOutputMapper() {
        return outputLineMapper;
    }

    @Override
    protected Optional<Font> outputLineToThemeMap(String line) {
        /*
         * We expect font strings of the scheme "'Noto Sans 10'"" or "'Noto Sans, 10'"" (with the single quotes).
         * It seems that if the font is set from KDE, then the name might be separated from the number by a comma.
         *
         * The name can be of any number of words, might also contain weight (e.g. Fira Sans Medium), and the
         * number at the end may contain decimal digits.
         */
        Matcher matcher = cmdOutputPattern.matcher(line);
        if (!matcher.matches()) {
            log.debug("Invalid line format: {}", line);
            return Optional.empty();
        }
        String fontName = matcher.group(1);
        String fontSizeStr = matcher.group(2);
        try {
            double fontSize = Double.parseDouble(fontSizeStr);
            Font font = new Font(fontName, fontSize);
            //log.debug("Font determined: {}", font);
            return Optional.of(font);
        } catch (IllegalArgumentException e) {
            log.debug("Font size is not a valid number: {}", fontSizeStr);
            return Optional.empty();
        }
    }

    private static String getDconfInterfaceSchema(DesktopEnvironment desktop) {
        return switch (desktop) {
            case GNOME, KDE, XFCE, UNKNOWN -> "org.gnome.desktop.interface";
            case CINNAMON -> "org.cinnamon.desktop.interface";
            case MATE -> "org.mate.interface";
        };
    }

}
