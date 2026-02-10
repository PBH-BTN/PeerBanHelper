package com.ghostchu.peerbanhelper.databasent.driver.h2;

import org.stone.beecp.BeeDataSource;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.databasent.driver.AbstractDatabaseDriver;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;

public class H2DatabaseDriver extends AbstractDatabaseDriver {
    private final File dbFile;
    private final String dbPath;
    private final ConfigurationSection section;
    private final AtomicBoolean requestCompactOnShutdown = new AtomicBoolean(false);
    private final BeeDataSource dataSource;

    public H2DatabaseDriver(@NotNull ConfigurationSection section) throws IOException {
        super();
        this.section = section;
        File persistDir = new File(Main.getDataDirectory(), "persist");
        if (!persistDir.exists()) {
            if (!persistDir.mkdirs()) {
                throw new IOException("Unable to create persist directory at " + persistDir.getAbsolutePath() + ", permission denied?");
            }
        }
        this.dbFile = new File(persistDir, "peerbanhelper-nt");
        this.dbPath = dbFile.getAbsolutePath();
        
        BeeDataSource beeDataSource = new BeeDataSource();
        beeDataSource.setJdbcUrl("jdbc:h2:" + this.dbPath + ";MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=60000;RETENTION_TIME=5000;MAX_LOG_SIZE=8");
        beeDataSource.setDriverClassName("org.h2.Driver");
        beeDataSource.setMaxActive(10);
        beeDataSource.setMaxWait(30000);
        beeDataSource.setIntervalOfClearTimeout(600000L);
        
        // 连接池验证配置
        beeDataSource.setAliveTestSql("SELECT 1");
        
        // 启用公平排队 (FIFO)
        beeDataSource.setFairMode(true);
        
        this.dataSource = beeDataSource;
    }

    @Override
    public @NotNull DatabaseType getType() {
        return DatabaseType.H2;
    }

    @Override
    public @NotNull DataSource createWriteDataSource() {
        // Hikari CP SQLite DataSource implementation
        return dataSource;
    }

    @Override
    protected @NotNull DataSource createReadDataSource() {
        return dataSource;
    }

    @Override
    public void close() throws Exception {
        try (Connection connection = getReadDataSource().getConnection()) {
            if (requestCompactOnShutdown.get()) {
                connection.createStatement().execute("SHUTDOWN COMPACT");
            } else {
                connection.createStatement().execute("SHUTDOWN");
            }
        }
        super.close();
    }
}
