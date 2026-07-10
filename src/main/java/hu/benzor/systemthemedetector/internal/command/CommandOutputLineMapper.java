package hu.benzor.systemthemedetector.internal.command;

import java.util.Optional;
import java.util.function.Function;

public interface CommandOutputLineMapper {

    <T> Optional<T> mapLine(Function<String, Optional<T>> lineMapper);

}
