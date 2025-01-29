package com.ghostchu.peerbanhelper.database;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.lab.Experiments;
import com.ghostchu.peerbanhelper.lab.Laboratory;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.j256.ormlite.field.DataPersisterManager;
import com.j256.ormlite.jdbc.JdbcSingleConnectionSource;
import com.j256.ormlite.jdbc.db.SqliteDatabaseType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Getter
@Slf4j
@Component
public final class Database {
    private final File sqliteDb;
    private final File dbMaintenanceFile;
    private final Laboratory laboratory;
    private JdbcSingleConnectionSource dataSource;
    private DatabaseHelper helper;

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

    private void registerPersisters() {
        DataPersisterManager.registerDataPersisters(TranslationComponentPersistener.getSingleton());
    }

    private long getLastMaintenanceTime() {
        if (!dbMaintenanceFile.exists()) {
            return 0;
        }
        try {
            return Long.parseLong(Files.readString(dbMaintenanceFile.toPath()));
        } catch (Exception e) {
            return 0;
        }
    }

    private void setLastMaintenanceTime(long time) {
        try {
            Files.writeString(dbMaintenanceFile.toPath(), String.valueOf(time));
        } catch (IOException e) {
        }
    }

    public void setupDatabase(File file) throws SQLException {
        Connection rawConnection = DriverManager.getConnection("jdbc:sqlite:" + file);
        if (ExternalSwitch.parse("pbh.database.disableSQLitePragmaSettings") == null) {
            try (var stmt = rawConnection.createStatement()) {
                stmt.executeUpdate("PRAGMA synchronous = NORMAL");
                stmt.executeUpdate("PRAGMA journal_mode = WAL");
                stmt.executeUpdate("PRAGMA mmap_size = 50331648");
                stmt.executeUpdate("PRAGMA OPTIMIZE");
                try {
                    if (System.currentTimeMillis() - getLastMaintenanceTime() >= Duration.ofDays(Main.getMainConfig().getInt("persist.vacuum-interval-days")).toMillis()) {
                        if (ExternalSwitch.parse("pbh.database.disableSQLiteVacuum") == null) {
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

    private void backupDatabase(File input, File output) throws IOException {
        try (var filein = new FileInputStream(input);
             var fileout = new FileOutputStream(output)) {
            MiscUtil.gzip(filein, fileout);
        }
    }

    public void close() {
        this.dataSource.closeQuietly();
    }
}
