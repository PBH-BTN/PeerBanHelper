package com.ghostchu.peerbanhelper.databasent.driver.sqlite;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.databasent.driver.AbstractDatabaseDriver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;

@Slf4j
public class SQLiteDatabaseDriver extends AbstractDatabaseDriver {
    private final File dbFile;
    private final String dbPath;
    private final ConfigurationSection section;

    public SQLiteDatabaseDriver(@NotNull ConfigurationSection section) throws IOException {
        super();
        this.section = section;
        File persistDir = new File(Main.getDataDirectory(), "persist");
        if (!persistDir.exists()) {
            if (!persistDir.mkdirs()) {
                throw new IOException("Unable to create persist directory at " + persistDir.getAbsolutePath() + ", permission denied?");
            }
        }
        this.dbFile = new File(persistDir, "peerbanhelper-nt.db");
        this.dbPath = dbFile.getAbsolutePath();

    }

    @Override
    public @NotNull DatabaseType getType() {
        return DatabaseType.SQLITE;
    }

    @Override
    protected @NotNull DataSource createReadDataSource() {
        var config = createDefaultHikariConfig();
        config.setMaximumPoolSize(Runtime.getRuntime().availableProcessors());
        SQLiteConfig sqLiteConfig = new SQLiteConfig();
        sqLiteConfig.setOpenMode(SQLiteOpenMode.OPEN_URI);
        sqLiteConfig.setOpenMode(SQLiteOpenMode.FULLMUTEX);
        config.addDataSourceProperty(SQLiteConfig.Pragma.OPEN_MODE.getPragmaName(), sqLiteConfig.getOpenModeFlags());
        return new HikariDataSource(config);
    }

    @Override
    protected @NotNull DataSource createWriteDataSource() {
        var config = createDefaultHikariConfig();
        config.setMaximumPoolSize(1);
        SQLiteConfig sqLiteConfig = new SQLiteConfig();
        sqLiteConfig.setOpenMode(SQLiteOpenMode.OPEN_URI);
        sqLiteConfig.setOpenMode(SQLiteOpenMode.NOMUTEX);
        config.addDataSourceProperty(SQLiteConfig.Pragma.OPEN_MODE.getPragmaName(), sqLiteConfig.getOpenModeFlags());
        return new HikariDataSource(config);
    }

    private HikariConfig createDefaultHikariConfig(){
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + this.dbPath);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setIdleTimeout(600000);
        config.setConnectionTimeout(30000);
        // SQLite-specific connection properties
        config.setThreadFactory(Thread.ofVirtual().name("HikariCP-SQLitePool").factory());
        config.addDataSourceProperty(SQLiteConfig.Pragma.JOURNAL_MODE.getPragmaName(), SQLiteConfig.JournalMode.WAL);
        config.addDataSourceProperty(SQLiteConfig.Pragma.SYNCHRONOUS.getPragmaName(), SQLiteConfig.SynchronousMode.NORMAL);
        config.addDataSourceProperty(SQLiteConfig.Pragma.JOURNAL_SIZE_LIMIT.getPragmaName(),67108864);
        config.addDataSourceProperty(SQLiteConfig.Pragma.MMAP_SIZE.getPragmaName(), 134217728);
        return config;
    }

    private void applyPragmaSettings(HikariDataSource dataSource) {
        if (ExternalSwitch.parse("pbh.database.disableSQLitePragmaSettings") != null) {
            log.info("SQLite PRAGMA settings are disabled by external switch");
            return;
        }

        try (Connection conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {

            stmt.executeUpdate("PRAGMA synchronous = NORMAL");
            stmt.executeUpdate("PRAGMA journal_mode = WAL");
            stmt.executeUpdate("PRAGMA mmap_size = 134217728");
            stmt.executeUpdate("PRAGMA transaction_mode = IMMEDIATE");
            stmt.executeUpdate("PRAGMA journal_size_limit = 67108864");
            //long softHeapLimit = ExternalSwitch.parseLong("pbh.database.sqliteSoftHeapLimitBytes", 33554432L);
            //stmt.executeUpdate("PRAGMA soft_heap_limit = " + softHeapLimit);

            stmt.executeUpdate("PRAGMA OPTIMIZE");

            log.debug("SQLite PRAGMA settings applied successfully");
        } catch (SQLException e) {
            log.warn("Failed to apply SQLite PRAGMA settings", e);
        }
    }

    @Override
    public void close() throws Exception {
        // Perform VACUUM if needed (based on last maintenance time)
        if (ExternalSwitch.parse("pbh.database.disableSQLiteVacuum") == null) {
            performVacuumIfNeeded();
        }
        super.close();
    }

    private void performVacuumIfNeeded() {
        try {
            File maintenanceFile = new File(dbFile.getParentFile(), "peerbanhelper-nt.db.maintenance");
            long lastMaintenance = 0;

            if (maintenanceFile.exists()) {
                String content = java.nio.file.Files.readString(maintenanceFile.toPath());
                lastMaintenance = Long.parseLong(content.trim());
            }

            long vacuumIntervalDays = Main.getMainConfig().getInt("persist.vacuum-interval-days", 7);
            long timeSinceLastMaintenance = System.currentTimeMillis() - lastMaintenance;

            if (timeSinceLastMaintenance >= Duration.ofDays(vacuumIntervalDays).toMillis()) {
                log.debug("Performing SQLite VACUUM maintenance (last performed {} days ago)", timeSinceLastMaintenance / (1000 * 60 * 60 * 24));
                try (Connection conn = getReadDataSource().getConnection();
                     var stmt = conn.createStatement()) {
                    stmt.execute("VACUUM");

                    // Update maintenance timestamp
                    java.nio.file.Files.writeString(maintenanceFile.toPath(),
                                                    String.valueOf(System.currentTimeMillis()));

                    log.info("SQLite database VACUUM completed successfully");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to perform SQLite VACUUM", e);
        }
    }
}
