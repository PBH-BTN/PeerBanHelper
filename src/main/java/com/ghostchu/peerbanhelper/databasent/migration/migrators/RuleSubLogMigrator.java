package com.ghostchu.peerbanhelper.databasent.migration.migrators;

import com.ghostchu.peerbanhelper.databasent.migration.MigrationContext;
import com.ghostchu.peerbanhelper.databasent.migration.TableMigrator;
import com.ghostchu.peerbanhelper.databasent.service.RuleSubLogService;
import com.ghostchu.peerbanhelper.databasent.table.RuleSubLogEntity;
import com.ghostchu.peerbanhelper.module.IPBanRuleUpdateType;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class RuleSubLogMigrator implements TableMigrator {
    private final RuleSubLogService ruleSubLogService;

    public RuleSubLogMigrator(RuleSubLogService ruleSubLogService) {
        this.ruleSubLogService = ruleSubLogService;
    }

    @Override
    public String getTableName() {
        return "rule_sub_log";
    }

    @Override
    public int getMigrationOrder() {
        return 81;
    }

    @Override
    public boolean isTableAvailable(Connection sqliteConnection) throws Exception {
        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='rule_sub_log'";
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery(query)) {
            return rs.next();
        }
    }

    @Override
    public long migrate(Connection sqliteConnection, MigrationContext context) throws Exception {
        log.info("Starting migration of rule_sub_log table...");

        String selectQuery = "SELECT id, ruleId, updateTime, count, updateType FROM rule_sub_log";
        long count = 0;
        long totalCount = getTotalCount(sqliteConnection);
        long lastLogged = 0;

        try (PreparedStatement ps = sqliteConnection.prepareStatement(selectQuery);
             ResultSet rs = ps.executeQuery()) {

            List<RuleSubLogEntity> batch = new ArrayList<>();

            while (rs.next()) {
                RuleSubLogEntity entity = new RuleSubLogEntity();
                entity.setId(rs.getLong("id"));
                entity.setRuleId(rs.getString("ruleId"));

                long updateTimeMillis = rs.getLong("updateTime");
                entity.setUpdateTime(OffsetDateTime.ofInstant(
                        Instant.ofEpochMilli(updateTimeMillis), ZoneId.systemDefault()));

                entity.setCount(rs.getInt("count"));

                String updateTypeStr = rs.getString("updateType");
                try {
                    entity.setUpdateType(IPBanRuleUpdateType.valueOf(updateTypeStr));
                } catch (Exception e) {
                    log.warn("Unknown update type: {}, defaulting to UNKNOWN", updateTypeStr);
                    // Set to null or a default value
                }

                batch.add(entity);

                if (batch.size() >= context.getBatchSize()) {
                    ruleSubLogService.getBaseMapper().insertOrUpdate(batch);
                    count += batch.size();
                    batch.clear();

                    if (MigrationContext.shouldLogProgress(count, totalCount, lastLogged)) {
                        log.info("Migrated {} / {} rule_sub_log records ({})",
                                count, totalCount, MigrationContext.formatProgress(count, totalCount));
                        lastLogged = count;
                    }
                }
            }

            if (!batch.isEmpty()) {
                ruleSubLogService.getBaseMapper().insertOrUpdate(batch);
                count += batch.size();
            }
        }

        log.info("Completed migration of rule_sub_log table: {} records", count);
        context.incrementTotalRecords(count);
        return count;
    }

    private long getTotalCount(Connection sqliteConnection) {
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM rule_sub_log")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.warn("Failed to get total count for rule_sub_log table", e);
        }
        return 0;
    }
}
