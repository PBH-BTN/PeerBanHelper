package com.ghostchu.peerbanhelper.database;

import com.ghostchu.peerbanhelper.database.table.*;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.logger.Level;
import com.j256.ormlite.logger.Logger;
import com.j256.ormlite.support.BaseConnectionSource;
import com.j256.ormlite.table.TableUtils;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
@Getter
public class DatabaseHelper {
    private final Database database;

    public DatabaseHelper(@Autowired Database database) throws SQLException {
        this.database = database;
        Logger.setGlobalLogLevel(Level.WARNING);
        createTables();

    }


    private void createTables() throws SQLException {
        TableUtils.createTableIfNotExists(database.getDataSource(), MetadataEntity.class);
        TableUtils.createTableIfNotExists(database.getDataSource(), PeerIdentityEntity.class);
        TableUtils.createTableIfNotExists(database.getDataSource(), TorrentEntity.class);
        TableUtils.createTableIfNotExists(database.getDataSource(), ModuleEntity.class);
        TableUtils.createTableIfNotExists(database.getDataSource(), RuleEntity.class);
        TableUtils.createTableIfNotExists(database.getDataSource(), HistoryEntity.class);
        TableUtils.createTableIfNotExists(database.getDataSource(), BanListEntity.class);
        TableUtils.createTableIfNotExists(database.getDataSource(), RuleSubInfoEntity.class);
        TableUtils.createTableIfNotExists(database.getDataSource(), RuleSubLogEntity.class);
    }

    private void performUpgrade() throws SQLException {
        Dao<MetadataEntity, String> metadata = DaoManager.createDao(getDataSource(), MetadataEntity.class);
        MetadataEntity version = metadata.createIfNotExists(new MetadataEntity("version", "1"));
        int v = Integer.parseInt(version.getValue());
        if (v == 0) {
            try {
                // so something
            } catch (Exception ignored) {
            }
            v++;
        }
        version.setValue(String.valueOf(v));
        metadata.update(version);
    }

    public BaseConnectionSource getDataSource() {
        return database.getDataSource();
    }


}
