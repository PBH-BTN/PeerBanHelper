package hu.benzor.systemthemedetector.internal.environment.propertyreader;

import java.util.Optional;

public class EnvironmentReader implements PropertyReader {

    @Override
    public Optional<String> getValue(String key) {
        return Optional.ofNullable(System.getenv(key));
    }

}
