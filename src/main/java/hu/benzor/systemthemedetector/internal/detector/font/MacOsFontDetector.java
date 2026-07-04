package hu.benzor.systemthemedetector.internal.detector.font;

import java.util.Optional;

import hu.benzor.systemthemedetector.api.theme.Theme.Font;
import hu.benzor.systemthemedetector.internal.command.CommandOutputLineMapper;
import hu.benzor.systemthemedetector.internal.command.DummyCommandOutputLineMapper;

public final class MacOsFontDetector extends FontDetector {

    private static final String FONT_NAME = ".SF NS Text";
    private static final double PT_SIZE = 13.0;

    private final CommandOutputLineMapper outputLineMapper;

    public MacOsFontDetector() {
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
