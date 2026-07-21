package hu.benzor.systemthemedetector.internal.detector.accentcolor;

import hu.benzor.systemthemedetector.api.theme.Theme.AccentColor;
import hu.benzor.systemthemedetector.internal.command.CommandOutputLineMapper;
import hu.benzor.systemthemedetector.internal.command.FilteredCommandOutputLineMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public final class WindowsAccentColorDetector extends AccentColorDetector {

    private final CommandOutputLineMapper outputLineMapper;

    public WindowsAccentColorDetector() {
        ProcessBuilder pb = new ProcessBuilder(
                "reg",
                "query",
                "HKCU\\Software\\Microsoft\\Windows\\DWM",
                "/v",
                "ColorizationColor"
        );
        outputLineMapper = new FilteredCommandOutputLineMapper(pb, "ColorizationColor");
    }

    @Override
    protected CommandOutputLineMapper commandOutputMapper() {
        return outputLineMapper;
    }

    @Override
    protected Optional<AccentColor> outputLineToThemeMap(String line) {
        /*
         * Output: ColorizationColor    REG_DWORD    0xAARRGGBB
         * Or    : ColorizationCOlor    RED_DWORD    0xRRGGBB
         * It appears that when the color is generated from the wallpaper, there is no alpha.
         * We ignore alpha anyways
         */
        String[] parts = line.split("REG_DWORD");
        if (parts.length != 2) {
            log.debug("Invalid line format: {}", line);
            return Optional.empty();
        }
        try {
            String colorId = parts[1].trim();
            if (colorId.length() != 10 && colorId.length() != 8) {
                log.debug("Invalid color string: {}", colorId);
                return Optional.empty();
            }
            long color = Long.decode(colorId);
            int r = (int) (color >>> 16) & 0xFF;
            int g = (int) (color >>> 8) & 0xFF;
            int b = (int) color & 0xFF;
            AccentColor accentColor = new AccentColor(r, g, b);
            //log.debug("Accent color determined: {}", accentColor);
            return Optional.of(accentColor);

        } catch (IllegalArgumentException e) {
            log.debug("Invalid color string", e);
            return Optional.empty();
        }
    }

}
