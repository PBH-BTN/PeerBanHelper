package com.ghostchu.peerbanhelper.databasent.driver;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;

public class H2DatabaseDriver extends AbstractDatabaseDriver {
    private final File dbFile;
    private final String dbPath;
    private final ConfigurationSection section;

    public H2DatabaseDriver(ConfigurationSection section) throws IOException {
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
    public @NotNull String getMapperPackagePath() {
        return "com.ghostchu.peerbanhelper.databasent.mapper.mysql"; // H2 使用 MySQL 方言
    }

    @Override
    public @NotNull DataSource getDataSource() {
        // Hikari CP SQLite DataSource implementation
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:" + this.dbPath + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
        config.setDriverClassName("org.h2.Driver");
        config.setMaximumPoolSize(section.getInt("pool.max-size"));
        config.setMinimumIdle(section.getInt("pool.min-idle"));
        config.setIdleTimeout(section.getLong("pool.idle-timeout-millis"));
        config.setThreadFactory(Thread.ofVirtual().name("HikariCP-H2Pool").factory());
        return new HikariDataSource(config);

    }
}
