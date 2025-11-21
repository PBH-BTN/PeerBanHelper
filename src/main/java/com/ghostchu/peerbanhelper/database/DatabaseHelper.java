package com.ghostchu.peerbanhelper.database;

import com.ghostchu.peerbanhelper.config.ConfigTransfer;
import com.ghostchu.peerbanhelper.database.table.*;
import com.ghostchu.peerbanhelper.database.table.PeerConnectionMetricsTrackEntity;
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
        createTables(true);
        performUpgrade();
        createTables(false);
    }
    private void createTables(boolean ignoreError) {
        Class<?>[] persistTable = new Class[]{
                MetadataEntity.class, TorrentEntity.class, ModuleEntity.class, RuleEntity.class, HistoryEntity.class,
                BanListEntity.class, RuleSubInfoEntity.class, RuleSubLogEntity.class, PeerRecordEntity.class,
                PCBAddressEntity.class, PCBRangeEntity.class, TrafficJournalEntity.class, AlertEntity.class,
                PeerConnectionMetricsEntity.class, PeerConnectionMetricsTrackEntity.class
        };
        Class<?>[] tempTable = new Class[]{
                TrackedSwarmEntity.class
        };
        performCreateTables(persistTable, ignoreError, false);
        performCreateTables(tempTable, ignoreError, true);
    }

    private void performCreateTables(Class<?>[] table, boolean ignoreError, boolean tempTable) {
        for (Class<?> aClass : table) {
            try {
                PBHTableUtils.createTableIfNotExists(database.getDataSource(), aClass, tempTable, ignoreError);
            } catch (Exception err) {
                if (!ignoreError) {
                    log.error("Unable to create table for class: " + aClass.getSimpleName(), err);
                }
            }
        }
    }

    private void performUpgrade() throws SQLException {
        Dao<MetadataEntity, String> metadata = DaoManager.createDao(getDataSource(), MetadataEntity.class);
        MetadataEntity version = metadata.createIfNotExists(new MetadataEntity("version", "20"));
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
            // dropped
            v = 4;
        }
        if (v == 4) {
            // dropped
            v = 5;
        }
        if (v == 5) {
            // dropped
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
        if (v <= 14) {
            try {
                database.getDataSource().getReadWriteConnection("history").executeStatement("ALTER TABLE history ADD COLUMN structuredData TEXT NOT NULL DEFAULT '{}'", DatabaseConnection.DEFAULT_RESULT_FLAGS);
            } catch (Exception err) {
                log.error("Unable to upgrade database schema", err);
            }
            v = 15;
        }
        if (v == 15) {
            try {
                var historyDao = DaoManager.createDao(getDataSource(), HistoryEntity.class);
                recordBatchUpdate("DownloaderName Converting (BanHistory)", historyDao, (historyEntity -> {
                    var downloaderName = historyEntity.getDownloader();
                    historyEntity.setDownloader(ConfigTransfer.downloaderNameToUUID.getOrDefault(downloaderName, downloaderName));
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
            v = 16;
        }
        if (v == 16) {
            // dropped
            v = 17;
        }
        if (v <= 18) {
            try {
                log.info("Dropping old banlist table and re-creating for IPAddress format change");
                TableUtils.clearTable(database.getDataSource(), BanListEntity.class);
                TableUtils.createTableIfNotExists(database.getDataSource(), BanListEntity.class);
            } catch (Exception err) {
                log.error("Unable to upgrade database schema", err);
            }
            v = 19;
        }
        if (v <= 19) {
            try {
                log.info("Adding port field to peer_records");
                database.getDataSource().getReadWriteConnection("peer_records").executeStatement("ALTER TABLE peer_records ADD COLUMN port INT NOT NULL DEFAULT 0", DatabaseConnection.DEFAULT_RESULT_FLAGS);
            } catch (Exception err) {
                log.error("Unable to upgrade database schema", err);
            }
            v = 20;
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
                    dao.createOrUpdate(entity);
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
