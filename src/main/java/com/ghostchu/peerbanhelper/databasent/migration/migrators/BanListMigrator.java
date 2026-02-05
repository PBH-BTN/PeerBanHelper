package com.ghostchu.peerbanhelper.databasent.migration.migrators;

import com.ghostchu.peerbanhelper.databasent.migration.MigrationContext;
import com.ghostchu.peerbanhelper.databasent.migration.TableMigrator;
import com.ghostchu.peerbanhelper.databasent.service.BanListService;
import com.ghostchu.peerbanhelper.databasent.table.BanListEntity;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

/**
 * Migrates banlist table
 * Note: According to DatabaseHelper v19, banlist was cleared during migration,
 * so migrated data may be empty
 */
@Slf4j
public class BanListMigrator implements TableMigrator {
    private final BanListService banListService;

    public BanListMigrator(BanListService banListService) {
        this.banListService = banListService;
    }

    @Override
    public String getTableName() {
        return "banlist";
    }

    @Override
    public int getMigrationOrder() {
        return 120;
    }

    @Override
    public boolean isTableAvailable(Connection sqliteConnection) throws Exception {
        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='banlist'";
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery(query)) {
            return rs.next();
        }
    }

    @Override
    public long migrate(Connection sqliteConnection, MigrationContext context) throws Exception {
        // Note: According to DatabaseHelper upgrade v19, banlist was cleared
        // We'll still migrate it if data exists, but it may be empty
        String selectQuery = "SELECT address, metadata FROM banlist";
        long count = 0;
        long totalCount = getTotalCount(sqliteConnection);
        long lastLogged = 0;

        try (PreparedStatement ps = sqliteConnection.prepareStatement(selectQuery);
             ResultSet rs = ps.executeQuery()) {

            List<BanListEntity> batch = new ArrayList<>();

            while (rs.next()) {
                try {
                    BanListEntity entity = new BanListEntity();
                    entity.setAddress(rs.getString("address"));
                    entity.setMetadata(rs.getString("metadata"));

                    batch.add(entity);

                    if (batch.size() >= context.getBatchSize()) {
                        banListService.getBaseMapper().insertOrUpdate(batch);
                        count += batch.size();
                        batch.clear();

                        if (MigrationContext.shouldLogProgress(count, totalCount, lastLogged)) {
                            log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_PROGRESS, count, totalCount, "banlist", MigrationContext.formatProgress(count, totalCount)));
                            lastLogged = count;
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to migrate banlist record: {}", e.getMessage());
                }
            }

            // Save remaining records
            if (!batch.isEmpty()) {
                banListService.getBaseMapper().insertOrUpdate(batch);
                count += batch.size();
            }
        }

        log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_COMPLETED, count, "banlist"));
        context.incrementTotalRecords(count);
        return count;
    }

    private long getTotalCount(Connection sqliteConnection) {
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM banlist")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.warn("Failed to get total count for banlist table", e);
        }
        return 0;
    }
}
