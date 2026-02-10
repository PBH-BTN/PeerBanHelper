package com.ghostchu.peerbanhelper.databasent.driver.sqlite;

import org.stone.beecp.BeeDataSource;
import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.databasent.DatabaseType;
import com.ghostchu.peerbanhelper.databasent.driver.AbstractDatabaseDriver;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
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
        BeeDataSource dataSource = createDefaultBeeDataSource();
        dataSource.setMaxActive(Runtime.getRuntime().availableProcessors());
        
        SQLiteConfig sqLiteConfig = new SQLiteConfig();
        sqLiteConfig.setOpenMode(SQLiteOpenMode.OPEN_URI);
        sqLiteConfig.setOpenMode(SQLiteOpenMode.FULLMUTEX);
        
        dataSource.addConnectionFactoryProperty(SQLiteConfig.Pragma.OPEN_MODE.getPragmaName(), 
            String.valueOf(sqLiteConfig.getOpenModeFlags()));
        
        return dataSource;
    }

    @Override
    protected @NotNull DataSource createWriteDataSource() {
        BeeDataSource dataSource = createDefaultBeeDataSource();
        dataSource.setMaxActive(1);
        
        SQLiteConfig sqLiteConfig = new SQLiteConfig();
        sqLiteConfig.setOpenMode(SQLiteOpenMode.OPEN_URI);
        sqLiteConfig.setOpenMode(SQLiteOpenMode.NOMUTEX);
        
        dataSource.addConnectionFactoryProperty(SQLiteConfig.Pragma.OPEN_MODE.getPragmaName(), 
            String.valueOf(sqLiteConfig.getOpenModeFlags()));
        
        return dataSource;
    }

    private BeeDataSource createDefaultBeeDataSource(){
        BeeDataSource dataSource = new BeeDataSource();
        dataSource.setJdbcUrl("jdbc:sqlite:" + this.dbPath);
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setMaxActive(4);
        dataSource.setMaxWait(30000);
        dataSource.setIntervalOfClearTimeout(600000L);
        
        // 连接池验证配置
        dataSource.setAliveTestSql("SELECT 1");
        
        // 启用公平排队 (FIFO)
        dataSource.setFairMode(true);
        
        // SQLite-specific connection properties
        dataSource.addConnectionFactoryProperty(SQLiteConfig.Pragma.JOURNAL_MODE.getPragmaName(), 
            SQLiteConfig.JournalMode.WAL.getValue());
        dataSource.addConnectionFactoryProperty(SQLiteConfig.Pragma.SYNCHRONOUS.getPragmaName(), 
            SQLiteConfig.SynchronousMode.NORMAL.getValue());
        dataSource.addConnectionFactoryProperty(SQLiteConfig.Pragma.JOURNAL_SIZE_LIMIT.getPragmaName(), 
            String.valueOf(67108864));
        dataSource.addConnectionFactoryProperty(SQLiteConfig.Pragma.MMAP_SIZE.getPragmaName(), 
            String.valueOf(134217728));
        
        return dataSource;
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
