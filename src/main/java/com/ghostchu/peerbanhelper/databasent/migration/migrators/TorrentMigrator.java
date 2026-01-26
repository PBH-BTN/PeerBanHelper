package com.ghostchu.peerbanhelper.databasent.migration.migrators;

import com.ghostchu.peerbanhelper.databasent.migration.MigrationContext;
import com.ghostchu.peerbanhelper.databasent.migration.TableMigrator;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

/**
 * Migrates torrents table
 */
@Slf4j
public class TorrentMigrator implements TableMigrator {
    private final TorrentService torrentService;

    public TorrentMigrator(TorrentService torrentService) {
        this.torrentService = torrentService;
    }

    @Override
    public String getTableName() {
        return "torrents";
    }

    @Override
    public int getMigrationOrder() {
        return 10; // Early, as other tables reference it
    }

    @Override
    public boolean isTableAvailable(Connection sqliteConnection) throws Exception {
        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='torrents'";
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery(query)) {
            return rs.next();
        }
    }

    @Override
    public long migrate(Connection sqliteConnection, MigrationContext context) throws Exception {
        String selectQuery = "SELECT id, infoHash, name, size, privateTorrent FROM torrents";
        long count = 0;
        long totalCount = getTotalCount(sqliteConnection);
        long lastLogged = 0;

        try (PreparedStatement ps = sqliteConnection.prepareStatement(selectQuery);
             ResultSet rs = ps.executeQuery()) {

            List<TorrentEntity> batch = new ArrayList<>();

            while (rs.next()) {
                long oldId = rs.getLong("id");
                String infoHash = rs.getString("infoHash");
                String name = rs.getString("name");
                long size = rs.getLong("size");
                Boolean privateTorrent = rs.getObject("privateTorrent") != null
                        ? rs.getBoolean("privateTorrent")
                        : null;

                TorrentEntity entity = new TorrentEntity();
                entity.setInfoHash(infoHash);
                entity.setName(name);
                entity.setSize(size);
                entity.setPrivateTorrent(privateTorrent);

                batch.add(entity);

                if (batch.size() >= context.getBatchSize()) {
                    saveBatch(batch, context, oldId);
                    count += batch.size();
                    batch.clear();

                    if (MigrationContext.shouldLogProgress(count, totalCount, lastLogged)) {
                        log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_PROGRESS, count, totalCount, "torrent", MigrationContext.formatProgress(count, totalCount)));

                        lastLogged = count;
                    }
                }
            }

            // Save remaining records
            if (!batch.isEmpty()) {
                saveBatch(batch, context, 0);
                count += batch.size();
            }
        }

        log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_COMPLETED, count, "torrent"));
        context.incrementTotalRecords(count);
        return count;
    }

    private long getTotalCount(Connection sqliteConnection) {
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM torrents")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.warn("Failed to get total count for torrents table", e);
        }
        return 0;
    }

    private void saveBatch(List<TorrentEntity> batch, MigrationContext context, long lastOldId) {
        torrentService.getBaseMapper().insertOrUpdate(batch);
    }
}
