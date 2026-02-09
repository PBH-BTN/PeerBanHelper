package com.ghostchu.peerbanhelper.databasent.driver.h2;

import com.alibaba.druid.pool.DruidDataSource;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.databasent.driver.AbstractDatabaseDriver;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

public class H2DatabaseDriver extends AbstractDatabaseDriver {
    private final File dbFile;
    private final String dbPath;
    private final ConfigurationSection section;
    private final AtomicBoolean requestCompactOnShutdown = new AtomicBoolean(false);
    private final DruidDataSource dataSource;

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
        
        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setUrl("jdbc:h2:" + this.dbPath + ";MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=60000;RETENTION_TIME=5000;MAX_LOG_SIZE=8");
        druidDataSource.setDriverClassName("org.h2.Driver");
        druidDataSource.setMaxActive(10);
        druidDataSource.setMinIdle(1);
        druidDataSource.setMaxWait(30000);
        druidDataSource.setTimeBetweenEvictionRunsMillis(600000);
        
        // 连接池验证配置
        druidDataSource.setValidationQuery("SELECT 1");
        druidDataSource.setTestWhileIdle(true);
        druidDataSource.setTestOnBorrow(false);
        druidDataSource.setTestOnReturn(false);
        
        // 启用 fairQueuing FIFO - 使用公平锁
        druidDataSource.setUseUnfairLock(false);
        
        // 启用 Druid 监控和防火墙
        try {
            druidDataSource.setFilters("stat,wall");
        } catch (SQLException e) {
            throw new IOException("Failed to set Druid filters", e);
        }
        
        this.dataSource = druidDataSource;
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
