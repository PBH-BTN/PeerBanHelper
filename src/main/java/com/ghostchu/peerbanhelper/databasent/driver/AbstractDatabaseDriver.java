package com.ghostchu.peerbanhelper.databasent.driver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.peerbanhelper.databasent.DatabaseDriver;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractDatabaseDriver implements DatabaseDriver {
    private final ObjectMapper objectMapper;

    public AbstractDatabaseDriver(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @NotNull
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
