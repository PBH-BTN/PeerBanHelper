package hu.benzor.systemthemedetector.internal.detector.accentcolor;

import hu.benzor.systemthemedetector.api.theme.Theme.AccentColor;
import hu.benzor.systemthemedetector.internal.detector.ThemeDetector;

public abstract sealed class AccentColorDetector extends ThemeDetector<AccentColor>
permits
    LinuxAccentColorDetector,
    MacOsAccentColorDetector,
    WindowsAccentColorDetector
{

    @Override
    protected final Class<AccentColor> type() {
        return AccentColor.class;
    }

    protected static int decimalToIntRgb(double decimal) {
        if (decimal < 0 || decimal > 1) {
            throw new IllegalArgumentException("Color member must be between 0 and 1. It was " + decimal + ".");
        }
        int scaled = (int) Math.round(decimal * 255);
        return Math.max(0, Math.min(scaled, 255));
    }

}
