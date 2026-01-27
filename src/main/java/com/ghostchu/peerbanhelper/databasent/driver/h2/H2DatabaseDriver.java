package com.ghostchu.peerbanhelper.databasent.driver.h2;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.databasent.driver.AbstractDatabaseDriver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
    }

    @Override
    public @NotNull DatabaseType getType() {
        return DatabaseType.H2;
    }

    @Override
    public @NotNull DataSource createDataSource() {
        // Hikari CP SQLite DataSource implementation
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:" + this.dbPath + ";MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=60000;RETENTION_TIME=5000;MAX_LOG_SIZE=8");
        config.setDriverClassName("org.h2.Driver");
        config.setMaximumPoolSize(section.getInt("pool.max-size"));
        config.setMinimumIdle(section.getInt("pool.min-idle"));
        config.setIdleTimeout(section.getLong("pool.idle-timeout-millis"));
        config.setThreadFactory(Thread.ofVirtual().name("HikariCP-H2Pool").factory());
        return new HikariDataSource(config);
    }

    @Override
    public void close() throws Exception {
        try (Connection connection = getDataSource().getConnection()) {
            if (requestCompactOnShutdown.get()) {
                connection.createStatement().execute("SHUTDOWN COMPACT");
            } else {
                connection.createStatement().execute("SHUTDOWN");
            }
        }
        super.close();
    }
}
