package hu.benzor.systemthemedetector.internal.detector.font;

import hu.benzor.systemthemedetector.api.theme.Theme.Font;
import hu.benzor.systemthemedetector.internal.command.CommandOutputLineMapper;
import hu.benzor.systemthemedetector.internal.command.DummyCommandOutputLineMapper;

import java.util.Optional;

public final class WindowsFontDetector extends FontDetector {

    private static final String FONT_NAME = "Segoe UI";
    private static final double PT_SIZE = 9.0;

    private final CommandOutputLineMapper outputLineMapper;

    public WindowsFontDetector() {
        this.outputLineMapper = new DummyCommandOutputLineMapper("");
    }

    @Override
    protected CommandOutputLineMapper commandOutputMapper() {
        return outputLineMapper;
    }

    @Override
    protected Optional<Font> outputLineToThemeMap(String line) {
        return Optional.of(new Font(FONT_NAME, PT_SIZE));
    }

}
