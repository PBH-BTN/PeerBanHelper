package com.ghostchu.peerbanhelper.database;

import com.ghostchu.peerbanhelper.database.table.*;
import com.ghostchu.peerbanhelper.telemetry.rollbar.RollbarErrorReporter;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.logger.Level;
import com.j256.ormlite.logger.Logger;
import com.j256.ormlite.support.BaseConnectionSource;
import com.j256.ormlite.table.TableUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
@Getter
@Slf4j
public class DatabaseHelper {
    private final Database database;
    private final RollbarErrorReporter rollbarErrorReporter;

    public DatabaseHelper(@Autowired Database database, RollbarErrorReporter rollbarErrorReporter) throws SQLException {
        this.database = database;
        this.rollbarErrorReporter = rollbarErrorReporter;
        Logger.setGlobalLogLevel(Level.WARNING);
        createTables();
        performUpgrade();
    }


    private void createTables() throws SQLException {
        TableUtils.createTableIfNotExists(database.getDataSource(), MetadataEntity.class);
        TableUtils.createTableIfNotExists(database.getDataSource(), TorrentEntity.class);
        TableUtils.createTableIfNotExists(database.getDataSource(), ModuleEntity.class);
        TableUtils.createTableIfNotExists(database.getDataSource(), RuleEntity.class);
        TableUtils.createTableIfNotExists(database.getDataSource(), HistoryEntity.class);
        TableUtils.createTableIfNotExists(database.getDataSource(), BanListEntity.class);
        TableUtils.createTableIfNotExists(database.getDataSource(), RuleSubInfoEntity.class);
        TableUtils.createTableIfNotExists(database.getDataSource(), RuleSubLogEntity.class);
        TableUtils.createTableIfNotExists(database.getDataSource(), PeerRecordEntity.class);
        TableUtils.createTableIfNotExists(database.getDataSource(), ProgressCheatBlockerPersistEntity.class);
        TableUtils.createTableIfNotExists(database.getDataSource(), TrafficJournalEntity.class);
    }

    private void performUpgrade() throws SQLException {
        Dao<MetadataEntity, String> metadata = DaoManager.createDao(getDataSource(), MetadataEntity.class);
        MetadataEntity version = metadata.createIfNotExists(new MetadataEntity("version", "6"));
        int v = Integer.parseInt(version.getValue());
        if (v < 3) {
            try {
                // so something
                var historyDao = DaoManager.createDao(getDataSource(), HistoryEntity.class);
                historyDao.executeRaw("ALTER TABLE " + historyDao.getTableName() + " ADD COLUMN downloader VARCHAR DEFAULT ''");
            } catch (Exception err) {
                rollbarErrorReporter.error(err);
                //log.error("Unable to upgrade database schema", err);
            }
            v = 3;
        }
        if (v == 3) {
            TableUtils.dropTable(getDataSource(), ProgressCheatBlockerPersistEntity.class, true);
            TableUtils.createTableIfNotExists(database.getDataSource(), ProgressCheatBlockerPersistEntity.class);
            v = 4;
        }
        if (v == 4) {
            TableUtils.dropTable(getDataSource(), ProgressCheatBlockerPersistEntity.class, true);
            TableUtils.createTableIfNotExists(database.getDataSource(), ProgressCheatBlockerPersistEntity.class);
            v = 5;
        }
        if (v == 5) {
            TableUtils.dropTable(getDataSource(), ProgressCheatBlockerPersistEntity.class, true);
            TableUtils.createTableIfNotExists(database.getDataSource(), ProgressCheatBlockerPersistEntity.class);
            v = 6;
        }
        version.setValue(String.valueOf(v));
        metadata.update(version);
    }

    public BaseConnectionSource getDataSource() {
        return database.getDataSource();
    }


}
