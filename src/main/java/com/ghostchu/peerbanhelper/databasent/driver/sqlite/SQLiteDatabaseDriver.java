package com.ghostchu.peerbanhelper.databasent.driver.sqlite;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.databasent.driver.AbstractDatabaseDriver;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.sqlite.SQLiteConfig;
import org.stone.beecp.BeeDataSource;
import org.stone.beecp.BeeDataSourceConfig;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.time.Duration;

import static com.ghostchu.peerbanhelper.util.MiscUtil.removeBeeCPShutdownHook;

@Slf4j
public class SQLiteDatabaseDriver extends AbstractDatabaseDriver {
    private final File dbFile;
    private final String dbPath;
    private final BeeDataSource dataSource;

    public SQLiteDatabaseDriver(@NotNull ConfigurationSection section) throws IOException {
        super();
        BeeDataSourceConfig config = new BeeDataSourceConfig();
        File persistDir = new File(Main.getDataDirectory(), "persist");
        if (!persistDir.exists()) {
            if (!persistDir.mkdirs()) {
                throw new IOException("Unable to create persist directory at " + persistDir.getAbsolutePath() + ", permission denied?");
            }
        }
        this.dbFile = new File(persistDir, "peerbanhelper-nt.db");
        this.dbPath = dbFile.getAbsolutePath();

        config.setJdbcUrl("jdbc:p6spy:sqlite:" + this.dbPath);
        //config.setDriverClassName("org.sqlite.JDBC");
        config.setDriverClassName("com.p6spy.engine.spy.P6SpyDriver");
        config.setMaxActive(1);
        config.setMaxWait(30000);
        config.setIntervalOfClearTimeout(600000L);
        // 连接池验证配置
        config.setAliveTestSql("SELECT 1");

        // 启用公平排队 (FIFO)
        config.setFairMode(true);

        // SQLite-specific connection properties
        config.addConnectionFactoryProperty(SQLiteConfig.Pragma.JOURNAL_MODE.getPragmaName(),
                SQLiteConfig.JournalMode.WAL.getValue());
        config.addConnectionFactoryProperty(SQLiteConfig.Pragma.SYNCHRONOUS.getPragmaName(),
                SQLiteConfig.SynchronousMode.NORMAL.getValue());
        config.addConnectionFactoryProperty(SQLiteConfig.Pragma.BUSY_TIMEOUT.getPragmaName(),
                String.valueOf(60000));
        config.addConnectionFactoryProperty(SQLiteConfig.Pragma.JOURNAL_SIZE_LIMIT.getPragmaName(),
                String.valueOf(67108864));
        config.addConnectionFactoryProperty(SQLiteConfig.Pragma.MMAP_SIZE.getPragmaName(),
                String.valueOf(134217728));

        this.dataSource = new BeeDataSource(config);
        removeBeeCPShutdownHook(dataSource);
    }

    @Override
    public @NotNull DatabaseType getType() {
        return DatabaseType.SQLITE;
    }

    @Override
    protected @NotNull DataSource createDataSource() {
        return dataSource;
    }

    @Override
    public void close() throws Exception {
        // Perform VACUUM if needed (based on last maintenance time)
//        if (ExternalSwitch.parse("pbh.database.disableSQLiteVacuum") == null) {
//            performVacuumIfNeeded();
//        }
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
                try (Connection conn = getDataSource().getConnection();
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