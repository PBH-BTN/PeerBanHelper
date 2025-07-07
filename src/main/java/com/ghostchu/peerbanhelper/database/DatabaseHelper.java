package com.ghostchu.peerbanhelper.database;

import com.ghostchu.peerbanhelper.config.ConfigTransfer;
import com.ghostchu.peerbanhelper.database.table.*;
import com.ghostchu.peerbanhelper.database.table.tmp.TrackedSwarmEntity;
import com.ghostchu.peerbanhelper.text.Lang;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.logger.Level;
import com.j256.ormlite.logger.Logger;
import com.j256.ormlite.support.BaseConnectionSource;
import com.j256.ormlite.support.DatabaseConnection;
import com.j256.ormlite.table.TableUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.function.Consumer;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

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
        PBHTableUtils.createTableIfNotExists(database.getDataSource(), MetadataEntity.class, false);
        PBHTableUtils.createTableIfNotExists(database.getDataSource(), TorrentEntity.class, false);
        PBHTableUtils.createTableIfNotExists(database.getDataSource(), ModuleEntity.class, false);
        PBHTableUtils.createTableIfNotExists(database.getDataSource(), RuleEntity.class, false);
        PBHTableUtils.createTableIfNotExists(database.getDataSource(), HistoryEntity.class, false);
        PBHTableUtils.createTableIfNotExists(database.getDataSource(), BanListEntity.class, false);
        PBHTableUtils.createTableIfNotExists(database.getDataSource(), RuleSubInfoEntity.class, false);
        PBHTableUtils.createTableIfNotExists(database.getDataSource(), RuleSubLogEntity.class, false);
        PBHTableUtils.createTableIfNotExists(database.getDataSource(), PeerRecordEntity.class, false);
        PBHTableUtils.createTableIfNotExists(database.getDataSource(), ProgressCheatBlockerPersistEntity.class, false);
        PBHTableUtils.createTableIfNotExists(database.getDataSource(), TrafficJournalEntity.class, false);
        PBHTableUtils.createTableIfNotExists(database.getDataSource(), AlertEntity.class, false);
        PBHTableUtils.createTableIfNotExists(database.getDataSource(), TrackedSwarmEntity.class, true);
    }

    private void performUpgrade() throws SQLException {
        Dao<MetadataEntity, String> metadata = DaoManager.createDao(getDataSource(), MetadataEntity.class);
        MetadataEntity version = metadata.createIfNotExists(new MetadataEntity("version", "13"));
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
        if (v == 9) {
            try {
                // add new column: privateTorrent, nullable
                var historyDao = DaoManager.createDao(getDataSource(), HistoryEntity.class);
                historyDao.executeRaw("ALTER TABLE " + historyDao.getTableName() + " ADD COLUMN downloaderProgress DOUBLE NULL");
            } catch (Exception err) {
                log.error("Unable to upgrade database schema", err);
            }
            v = 10;
        }
        if (v == 10) {
            try {
                database.getDataSource().getReadWriteConnection("peer_records").executeStatement("ALTER TABLE peer_records ADD COLUMN uploadSpeed BIGINT NOT NULL DEFAULT 0", DatabaseConnection.DEFAULT_RESULT_FLAGS);
                database.getDataSource().getReadWriteConnection("peer_records").executeStatement("ALTER TABLE peer_records ADD COLUMN downloadSpeed BIGINT NOT NULL DEFAULT 0", DatabaseConnection.DEFAULT_RESULT_FLAGS);
            } catch (Exception err) {
                log.error("Unable to upgrade database schema", err);
            }
            v = 11;
        }
        if (v == 11) {
            try {
                var historyDao = DaoManager.createDao(getDataSource(), HistoryEntity.class);
                recordBatchUpdate("DownloaderName Converting (BanHistory)", historyDao, (historyEntity -> {
                    var downloaderName = historyEntity.getDownloader();
                    historyEntity.setDownloader(ConfigTransfer.downloaderNameToUUID.getOrDefault(downloaderName, downloaderName));
                }));
                var peerRecordDao = DaoManager.createDao(getDataSource(), PeerRecordEntity.class);
                recordBatchUpdate("DownloaderName Converting (PeerRecord)", peerRecordDao, (peerRecordEntity -> {
                    var downloaderName = peerRecordEntity.getDownloader();
                    peerRecordEntity.setDownloader(ConfigTransfer.downloaderNameToUUID.getOrDefault(downloaderName, downloaderName));
                }));
                var progressCheatBlockerPersistDao = DaoManager.createDao(getDataSource(), ProgressCheatBlockerPersistEntity.class);
                recordBatchUpdate("DownloaderName Converting (ProgressCheatBlockerPersist)", progressCheatBlockerPersistDao, (progressCheatBlockerPersistEntity -> {
                    var downloaderName = progressCheatBlockerPersistEntity.getDownloader();
                    progressCheatBlockerPersistEntity.setDownloader(ConfigTransfer.downloaderNameToUUID.getOrDefault(downloaderName, downloaderName));
                }));
                var trafficJournalDao = DaoManager.createDao(getDataSource(), TrafficJournalEntity.class);
                recordBatchUpdate("DownloaderName Converting (TrafficJournal)", trafficJournalDao, (trafficJournalEntity -> {
                    var downloaderName = trafficJournalEntity.getDownloader();
                    trafficJournalEntity.setDownloader(ConfigTransfer.downloaderNameToUUID.getOrDefault(downloaderName, downloaderName));
                }));
                TableUtils.clearTable(getDataSource(), BanListEntity.class);
            } catch (Exception err) {
                log.error("Unable to upgrade database schema", err);
            }
            v = 12;
        }
        if (v == 12) {
            try {
                database.getDataSource().getReadWriteConnection("history").executeStatement("ALTER TABLE history ADD COLUMN structuredData TEXT NOT NULL DEFAULT '{}'", DatabaseConnection.DEFAULT_RESULT_FLAGS);
            } catch (Exception err) {
                log.error("Unable to upgrade database schema", err);
            }
            v = 13;
        }
        version.setValue(String.valueOf(v));
        metadata.update(version);
    }

    private <T> void recordBatchUpdate(String processName, Dao<T, ?> dao, Consumer<T> consumer) throws Exception {
        long total = dao.countOf();

        dao.callBatchTasks(() -> {
            long processing = 0;
            for (T entity : dao.getWrappedIterable()) {
                try {
                    processing++;
                    if (processing % 10 == 0 || processing == total) {
                        log.info(tlUI(Lang.DATABASE_UPGRADING_RECORDS, processName, processing, total));
                    }
                    consumer.accept(entity);
                    dao.update(entity);
                } catch (Exception e) {
                    log.error("Unhandled error", e);
                }
            }
            return null;
        });
    }

    public BaseConnectionSource getDataSource() {
        return database.getDataSource();
    }


}
