package hu.benzor.systemthemedetector.internal.detector.appearance;

import hu.benzor.systemthemedetector.api.theme.Theme.Appearance;
import hu.benzor.systemthemedetector.internal.detector.ThemeDetector;

public abstract sealed class AppearanceDetector extends ThemeDetector<Appearance>
permits
    LinuxAppearanceDetector,
    MacOsAppearanceDetector,
    WindowsAppearanceDetector
{

    @Override
    protected final Class<Appearance> type() {
        return Appearance.class;
    }

}
