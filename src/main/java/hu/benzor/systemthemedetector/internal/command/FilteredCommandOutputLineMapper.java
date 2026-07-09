package hu.benzor.systemthemedetector.internal.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class FilteredCommandOutputLineMapper implements CommandOutputLineMapper {

    private final ProcessBuilder processBuilder;
    private final String filter;
    private final boolean passBlankStringOnReadFailure;

    public FilteredCommandOutputLineMapper(ProcessBuilder processBuilder) {
        this(processBuilder, null, false);
    }

    public FilteredCommandOutputLineMapper(ProcessBuilder processBuilder, String filter) {
        this(processBuilder, filter, false);
    }

    public FilteredCommandOutputLineMapper(ProcessBuilder processBuilder, boolean passBlankStringOnReadFailure) {
        this(processBuilder, null, passBlankStringOnReadFailure);
    }

    public <T> Optional<T> mapLine(Function<String, Optional<T>> lineMapper) {
        Optional<String> line = Optional.empty();
        try {
            Process process = processBuilder.start();
            try (BufferedReader reader = process.inputReader()) {
                line = reader.lines().filter(s -> filter == null || s.contains(filter)).findFirst();
            } finally {
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
        } catch (IOException | IndexOutOfBoundsException e){
            log.debug("Failed to read process output", e);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        if (passBlankStringOnReadFailure) {
            String unwrappedLine = line.orElse("");
            return lineMapper.apply(unwrappedLine);
        }
        return line.flatMap(lineMapper);
    }
}
