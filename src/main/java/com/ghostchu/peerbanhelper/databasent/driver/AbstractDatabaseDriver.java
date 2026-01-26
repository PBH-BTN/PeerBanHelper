package com.ghostchu.peerbanhelper.databasent.driver;

import com.ghostchu.peerbanhelper.databasent.DatabaseDriver;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;

@Slf4j
public abstract class AbstractDatabaseDriver implements DatabaseDriver {
    private DataSource dataSource;
    @Override
    public @NotNull String getMapperXmlPath() {
        return "mapper/" + getType().getMapperType() + "/**/*.xml"; // H2 使用 MySQL 方言
    }

    @Override
    public void close() throws Exception {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            if (!hikariDataSource.isClosed())
                hikariDataSource.close();
        } else {
            log.warn("Given DataSource is not an instance of HikariDataSource, cannot close it properly.");
        }
    }

    @Override
    public @NotNull DataSource getDataSource() {
        if (dataSource != null) return dataSource;
        dataSource = createDataSource();
        return dataSource;
    }

    @NotNull
    protected abstract DataSource createDataSource();
}
