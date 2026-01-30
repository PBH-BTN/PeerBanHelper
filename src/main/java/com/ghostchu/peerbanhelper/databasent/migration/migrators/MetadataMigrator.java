package com.ghostchu.peerbanhelper.databasent.migration.migrators;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghostchu.peerbanhelper.databasent.migration.MigrationContext;
import com.ghostchu.peerbanhelper.databasent.migration.TableMigrator;
import com.ghostchu.peerbanhelper.databasent.service.MetadataService;
import com.ghostchu.peerbanhelper.databasent.table.MetadataEntity;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

/**
 * Migrates metadata table
 * Note: Field names changed from key/value to k/v
 */
@Slf4j
public class MetadataMigrator implements TableMigrator {
    private final MetadataService metadataService;

    public MetadataMigrator(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Override
    public String getTableName() {
        return "metadata";
    }

    @Override
    public int getMigrationOrder() {
        return 1; // Migrate first as it contains version info
    }

    @Override
    public boolean isTableAvailable(Connection sqliteConnection) throws Exception {
        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='metadata'";
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery(query)) {
            return rs.next();
        }
    }

    @Override
    public long migrate(Connection sqliteConnection, MigrationContext context) throws Exception {
        String selectQuery = "SELECT \"key\", \"value\" FROM metadata";
        long count = 0;
        long totalCount = getTotalCount(sqliteConnection);
        long lastLogged = 0;

        try (PreparedStatement ps = sqliteConnection.prepareStatement(selectQuery);
             ResultSet rs = ps.executeQuery()) {

            List<MetadataEntity> batch = new ArrayList<>();

            while (rs.next()) {
                String key = rs.getString("key");
                String value = rs.getString("value");

                // Skip version metadata as it's handled separately
                if ("version".equals(key)) {
                    continue;
                }

                MetadataEntity entity = new MetadataEntity();
                entity.setK(key);
                entity.setV(value);

                batch.add(entity);

                if (batch.size() >= context.getBatchSize()) {
                    saveBatch(batch);
                    count += batch.size();
                    batch.clear();

                    if (MigrationContext.shouldLogProgress(count, totalCount, lastLogged)) {
                        log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_PROGRESS, count, totalCount, "metadata", MigrationContext.formatProgress(count, totalCount)));
                        lastLogged = count;
                    }
                }
            }

            // Save remaining records
            if (!batch.isEmpty()) {
                saveBatch(batch);
                count += batch.size();
            }
        }

        log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_COMPLETED, count, "metadata"));
        context.incrementTotalRecords(count);
        return count;
    }

    private long getTotalCount(Connection sqliteConnection) {
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM metadata WHERE \"key\" != 'version'")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.warn("Failed to get total count for metadata table", e);
        }
        return 0;
    }

    private void saveBatch(List<MetadataEntity> batch) {
        for (MetadataEntity entity : batch) {
            // Check if exists and update, or insert
            MetadataEntity existing = metadataService.getOne(
                    new LambdaQueryWrapper<MetadataEntity>().eq(MetadataEntity::getK, entity.getK())
            );
            if (existing != null) {
                // Update with SQLite data (SQLite takes priority)
                existing.setV(entity.getV());
                metadataService.updateById(existing);
            } else {
                metadataService.save(entity);
            }
        }
    }
}
