package hu.benzor.systemthemedetector.internal.detector.font;

import hu.benzor.systemthemedetector.api.theme.Theme.Font;
import hu.benzor.systemthemedetector.internal.detector.ThemeDetector;

public abstract sealed class FontDetector extends ThemeDetector<Font>
permits
    LinuxFontDetector,
    MacOsFontDetector,
    WindowsFontDetector
{

    @Override
    protected final Class<Font> type() {
        return Font.class;
    }

}
