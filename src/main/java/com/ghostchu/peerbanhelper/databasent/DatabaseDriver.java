package com.ghostchu.peerbanhelper.databasent;

import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;

public interface DatabaseDriver {
    @NotNull DatabaseType getType();

    @NotNull String getMapperXmlPath();

    @NotNull DataSource getReadDataSource();

    void close() throws Exception;

    @NotNull DataSource getWriteDataSource();
}
