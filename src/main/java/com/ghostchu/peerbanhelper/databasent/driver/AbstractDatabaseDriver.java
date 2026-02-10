package com.ghostchu.peerbanhelper.databasent.driver;

import org.stone.beecp.BeeDataSource;
import com.ghostchu.peerbanhelper.databasent.DatabaseDriver;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;

@Slf4j
public abstract class AbstractDatabaseDriver implements DatabaseDriver {
    private DataSource readDataSource;
    private DataSource writeDataSource;

    @Override
    public @NotNull String getMapperXmlPath() {
        return "mapper/" + getType().getMapperType() + "/**/*.xml"; // H2 使用 MySQL 方言
    }

    @Override
    public void close() throws Exception {
        if (readDataSource instanceof BeeDataSource beeDataSource) {
            if (!beeDataSource.isClosed())
                beeDataSource.close();
        } else {
            log.warn("Given DataSource is not an instance of BeeDataSource, cannot close it properly.");
        }
        if (writeDataSource instanceof BeeDataSource beeDataSource) {
            if (!beeDataSource.isClosed())
                beeDataSource.close();
        } else {
            log.warn("Given DataSource is not an instance of BeeDataSource, cannot close it properly.");
        }
    }

    @Override
    public @NotNull DataSource getReadDataSource() {
        if (readDataSource != null) return readDataSource;
        readDataSource = createReadDataSource();
        return readDataSource;
    }

    @Override
    public @NotNull DataSource getWriteDataSource() {
        if (writeDataSource != null) return writeDataSource;
        writeDataSource = createWriteDataSource();
        return writeDataSource;
    }

    @NotNull
    protected abstract DataSource createReadDataSource();


    @NotNull
    protected abstract DataSource createWriteDataSource();
}
