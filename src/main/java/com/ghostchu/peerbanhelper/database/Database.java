package com.ghostchu.peerbanhelper.database;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.lab.Experiments;
import com.ghostchu.peerbanhelper.lab.Laboratory;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.j256.ormlite.field.DataPersisterManager;
import com.j256.ormlite.jdbc.JdbcSingleConnectionSource;
import com.j256.ormlite.jdbc.db.SqliteDatabaseType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Getter
@Slf4j
@Component
public class Database {
    private final File sqliteDb;
    private final File dbMaintenanceFile;
    private final Laboratory laboratory;
    private JdbcSingleConnectionSource dataSource;
    private HikariDataSource hikari;
    private DatabaseHelper helper;

    /**
     * Constructs a new Database instance for the PeerBanHelper application.
     *
     * Initializes the database by creating the necessary directory structure,
     * setting up the SQLite database file and maintenance tracking file,
     * registering data persisters, and configuring the database connection.
     *
     * @param laboratory The Laboratory instance for managing application experiments
     * @throws SQLException if there is an error setting up the database connection
     * @throws ClassNotFoundException if required database driver classes cannot be loaded
     */
    public Database(Laboratory laboratory) throws SQLException, ClassNotFoundException {
        this.laboratory = laboratory;
        File databaseDirectory = new File(Main.getDataDirectory(), "persist");
        if (!databaseDirectory.exists()) {
            databaseDirectory.mkdirs();
        }
        this.sqliteDb = new File(databaseDirectory, "peerbanhelper.db");
        this.dbMaintenanceFile = new File(databaseDirectory, "peerbanhelper.db.maintenance");
        registerPersisters();
        setupDatabase(sqliteDb);

    }

    /**
     * Registers custom data persisters with the ORMLite DataPersisterManager.
     *
     * This method adds the TranslationComponentPersistener singleton to the DataPersisterManager,
     * enabling custom data persistence for translation components in the database.
     *
     * @see TranslationComponentPersistener
     * @see DataPersisterManager
     */
    private void registerPersisters() {
        DataPersisterManager.registerDataPersisters(TranslationComponentPersistener.getSingleton());
    }

    /**
     * Retrieves the timestamp of the last database maintenance operation.
     *
     * @return The last maintenance timestamp as a long value. Returns 0 if the maintenance file
     *         does not exist or cannot be read due to an I/O error.
     */
    private long getLastMaintenanceTime() {
        if (!dbMaintenanceFile.exists()) {
            return 0;
        }
        try {
            return Long.parseLong(Files.readString(dbMaintenanceFile.toPath()));
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Updates the last database maintenance timestamp in the maintenance file.
     *
     * @param time The timestamp representing the last maintenance time in milliseconds.
     *             Silently handles any IO errors during file writing to prevent disruption.
     */
    private void setLastMaintenanceTime(long time) {
        try {
            Files.writeString(dbMaintenanceFile.toPath(), String.valueOf(time));
        } catch (IOException e) {
        }
    }

    /**
     * Sets up the SQLite database connection with optimized configuration and maintenance.
     *
     * This method configures a HikariCP connection pool for the SQLite database, applies performance-enhancing PRAGMA settings,
     * and optionally performs a database vacuum operation based on a configured interval.
     *
     * @param file The SQLite database file to connect to
     * @throws SQLException If there is an error establishing the database connection or executing SQL statements
     *
     * @see HikariConfig
     * @see HikariDataSource
     */
    public void setupDatabase(File file) throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("PeerBanHelper SQLite Connection Pool");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + file);
        config.setConnectionTestQuery("SELECT 1");
        config.setMaximumPoolSize(1); // 50 Connections (including idle connections)
        this.hikari = new HikariDataSource(config);
        Connection rawConnection = this.hikari.getConnection();
        if (System.getProperty("disableSQLitePragmaSettings") == null) {
            try (var stmt = rawConnection.createStatement()) {
                stmt.executeUpdate("PRAGMA synchronous = NORMAL");
                stmt.executeUpdate("PRAGMA journal_mode = WAL");
                stmt.executeUpdate("PRAGMA auto_vacuum = INCREMENTAL");
                try {
                    if (System.currentTimeMillis() - getLastMaintenanceTime() >= Duration.ofDays(Main.getMainConfig().getInt("persist.vacuum-interval-days")).toMillis()) {
                        if (System.getProperty("pbh.disableSQLiteVacuum") == null) {
                            if (laboratory.isExperimentActivated(Experiments.SQLITE_VACUUM.getExperiment())) {
                                log.info(tlUI(Lang.SQLITE_VACUUM_BACKUP));
                                // 防强关备份
                                File outputBackup = new File(file.getParentFile(), file.getName() + ".bak.gz");
                                log.info(tlUI(Lang.SQLITE_VACUUM_BACKUP_COMPLETED));
                                for (int i = 0; i < 10; i++) {
                                    log.info(tlUI(Lang.SQLITE_VACUUM_IN_PROGRESS));
                                }
                                try {
                                    backupDatabase(file, outputBackup);
                                    long fileSize = file.length();
                                    stmt.executeUpdate("VACUUM;");
                                    long newFileSize = file.length();
                                    log.info(tlUI(Lang.SQLITE_VACUUM_SUCCESS, MsgUtil.humanReadableByteCountBin(fileSize), MsgUtil.humanReadableByteCountBin(newFileSize)));
                                } catch (IOException e) {
                                    log.warn(tlUI(Lang.SQLITE_VACUUM_BACKUP_FAILED), e);
                                } finally {
                                    // 太好了，我们没有被强关
                                    outputBackup.delete();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn(tlUI(Lang.UNABLE_SET_SQLITE_OPTIMIZED_PRAGMA), e);
                } finally {
                    setLastMaintenanceTime(System.currentTimeMillis());
                }
            } catch (Exception e) {
                log.warn(tlUI(Lang.UNABLE_SET_SQLITE_OPTIMIZED_PRAGMA), e);
            }
        }
        this.dataSource = new JdbcSingleConnectionSource("jdbc:sqlite:" + file, new SqliteDatabaseType(), rawConnection);
        this.helper = new DatabaseHelper(this);
        //  this.dataSource = new DataSourceConnectionSource( new HikariDataSource(config), new SqliteDatabaseType());
    }

    /**
     * Creates a gzip-compressed backup of a database file.
     *
     * @param input  The source database file to be backed up
     * @param output The destination file where the compressed backup will be stored
     * @throws IOException If an error occurs during file reading, writing, or compression
     */
    private void backupDatabase(File input, File output) throws IOException {
        try (var filein = new FileInputStream(input);
             var fileout = new FileOutputStream(output)) {
            MiscUtil.gzip(filein, fileout);
        }
    }

    /**
     * Closes the database connection source quietly.
     *
     * This method releases database resources associated with the data source
     * without throwing any exceptions. It is typically used for safely shutting
     * down the database connection pool when the application is terminating.
     *
     * @see HikariDataSource#close()
     */
    public void close() {
        this.dataSource.closeQuietly();
    }
}
