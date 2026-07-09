package hu.benzor.systemthemedetector.internal.environment.propertyreader;

import java.util.Optional;

public class SystemPropertyReader implements PropertyReader {

    @Override
    public Optional<String> getValue(String key) {
        return Optional.ofNullable(System.getProperty(key));
    }

}
