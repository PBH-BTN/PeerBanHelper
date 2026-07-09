package hu.benzor.systemthemedetector.internal.environment.propertyreader;

import java.util.Optional;

public interface PropertyReader {

    Optional<String> getValue(String key);

}
