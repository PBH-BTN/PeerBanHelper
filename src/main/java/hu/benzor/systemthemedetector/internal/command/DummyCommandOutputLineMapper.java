package hu.benzor.systemthemedetector.internal.command;

import java.util.Optional;
import java.util.function.Function;

public class DummyCommandOutputLineMapper implements CommandOutputLineMapper {

    private final String line;

    public DummyCommandOutputLineMapper(String line) {
        this.line = line;
    }

    @Override
    public <T> Optional<T> mapLine(Function<String, Optional<T>> lineMapper) {
        return lineMapper.apply(line);
    }

}
