package com.ghostchu.peerbanhelper.databasent.driver;

import com.ghostchu.peerbanhelper.databasent.DatabaseDriver;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractDatabaseDriver implements DatabaseDriver {
    @Override
    public @NotNull String getMapperXmlPath() {
        return "mapper/" + getType().getMapperType() + "/**/*.xml"; // H2 使用 MySQL 方言
    }
}
