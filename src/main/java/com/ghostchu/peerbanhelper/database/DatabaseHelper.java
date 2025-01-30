package com.ghostchu.peerbanhelper.database;

import com.ghostchu.peerbanhelper.database.table.*;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.logger.Level;
import com.j256.ormlite.logger.Logger;
import com.j256.ormlite.support.BaseConnectionSource;
import com.j256.ormlite.table.TableUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;

@Getter
@Slf4j
public final class DatabaseHelper {
    private final Database database;

    public DatabaseHelper(@Autowired Database database) throws SQLException {
        this.database = database;
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
        TableUtils.createTableIfNotExists(database.getDataSource(), AlertEntity.class);
    }

    private void performUpgrade() throws SQLException {
        Dao<MetadataEntity, String> metadata = DaoManager.createDao(getDataSource(), MetadataEntity.class);
        MetadataEntity version = metadata.createIfNotExists(new MetadataEntity("version", "9"));
        int v = Integer.parseInt(version.getValue());
        if (v < 3) {
            try {
                // so something
                var historyDao = DaoManager.createDao(getDataSource(), HistoryEntity.class);
                historyDao.executeRaw("ALTER TABLE " + historyDao.getTableName() + " ADD COLUMN downloader VARCHAR DEFAULT ''");
            } catch (Exception err) {
                log.error("Unable to upgrade database schema", err);
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
        if (v == 6) {
            TableUtils.dropTable(getDataSource(), AlertEntity.class, true);
            TableUtils.createTableIfNotExists(database.getDataSource(), AlertEntity.class);
            v = 7;
        }
        if (v == 7) {
            TableUtils.dropTable(getDataSource(), AlertEntity.class, true);
            TableUtils.createTableIfNotExists(database.getDataSource(), AlertEntity.class);
            v = 8;
        }
        if (v == 8) {
            try {
                // add new column: privateTorrent, nullable
                var torrentDao = DaoManager.createDao(getDataSource(), TorrentEntity.class);
                torrentDao.executeRaw("ALTER TABLE " + torrentDao.getTableName() + " ADD COLUMN privateTorrent BOOLEAN NULL");
            } catch (Exception err) {
                log.error("Unable to upgrade database schema", err);
            }
            v = 9;
        }
        version.setValue(String.valueOf(v));
        metadata.update(version);
    }

    public BaseConnectionSource getDataSource() {
        return database.getDataSource();
    }


}
