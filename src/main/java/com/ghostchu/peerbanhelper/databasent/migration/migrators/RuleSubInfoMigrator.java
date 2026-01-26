package com.ghostchu.peerbanhelper.databasent.migration.migrators;

import com.ghostchu.peerbanhelper.databasent.migration.MigrationContext;
import com.ghostchu.peerbanhelper.databasent.migration.TableMigrator;
import com.ghostchu.peerbanhelper.databasent.service.RuleSubInfoService;
import com.ghostchu.peerbanhelper.databasent.table.RuleSubInfoEntity;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class RuleSubInfoMigrator implements TableMigrator {
    private final RuleSubInfoService ruleSubInfoService;

    public RuleSubInfoMigrator(RuleSubInfoService ruleSubInfoService) {
        this.ruleSubInfoService = ruleSubInfoService;
    }

    @Override
    public String getTableName() {
        return "rule_sub_info";
    }

    @Override
    public int getMigrationOrder() {
        return 80;
    }

    @Override
    public boolean isTableAvailable(Connection sqliteConnection) throws Exception {
        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='rule_sub_info'";
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery(query)) {
            return rs.next();
        }
    }

    @Override
    public long migrate(Connection sqliteConnection, MigrationContext context) throws Exception {
        String selectQuery = "SELECT ruleId, enabled, ruleName, subUrl, lastUpdate, entCount FROM rule_sub_info";
        long count = 0;
        long totalCount = getTotalCount(sqliteConnection);
        long lastLogged = 0;

        try (PreparedStatement ps = sqliteConnection.prepareStatement(selectQuery);
             ResultSet rs = ps.executeQuery()) {

            List<RuleSubInfoEntity> batch = new ArrayList<>();

            while (rs.next()) {
                RuleSubInfoEntity entity = new RuleSubInfoEntity();
                entity.setRuleId(rs.getString("ruleId"));
                entity.setEnabled(rs.getBoolean("enabled"));
                entity.setRuleName(rs.getString("ruleName"));
                entity.setSubUrl(rs.getString("subUrl"));

                long lastUpdateMillis = rs.getLong("lastUpdate");
                if (lastUpdateMillis > 0) {
                    entity.setLastUpdate(OffsetDateTime.ofInstant(
                            Instant.ofEpochMilli(lastUpdateMillis), ZoneId.systemDefault()));
                }

                entity.setEntCount(rs.getInt("entCount"));

                batch.add(entity);

                if (batch.size() >= context.getBatchSize()) {
                    ruleSubInfoService.getBaseMapper().insertOrUpdate(batch);
                    count += batch.size();
                    batch.clear();

                    if (MigrationContext.shouldLogProgress(count, totalCount, lastLogged)) {
                        log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_PROGRESS, count, totalCount, "rule_sub_info", MigrationContext.formatProgress(count, totalCount)));

                        lastLogged = count;
                    }
                }
            }

            if (!batch.isEmpty()) {
                ruleSubInfoService.getBaseMapper().insertOrUpdate(batch);
                count += batch.size();
            }
        }

        log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_COMPLETED, count, "rule_sub_info"));
        context.incrementTotalRecords(count);
        return count;
    }

    private long getTotalCount(Connection sqliteConnection) {
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM rule_sub_info")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.warn("Failed to get total count for rule_sub_info table", e);
        }
        return 0;
    }
}
