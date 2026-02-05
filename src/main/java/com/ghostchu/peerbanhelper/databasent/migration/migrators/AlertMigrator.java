package com.ghostchu.peerbanhelper.databasent.migration.migrators;

import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.databasent.migration.MigrationContext;
import com.ghostchu.peerbanhelper.databasent.migration.TableMigrator;
import com.ghostchu.peerbanhelper.databasent.service.AlertService;
import com.ghostchu.peerbanhelper.databasent.table.AlertEntity;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

/**
 * Migrates alert table
 * Note: Service may not be available, using placeholder for now
 */
@Slf4j
public class AlertMigrator implements TableMigrator {
    private final AlertService alertService;

    public AlertMigrator(AlertService alertService) {
        this.alertService = alertService;
    }

    @Override
    public String getTableName() {
        return "alert";
    }

    @Override
    public int getMigrationOrder() {
        return 110;
    }

    @Override
    public boolean isTableAvailable(Connection sqliteConnection) throws Exception {
        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='alert'";
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery(query)) {
            return rs.next();
        }
    }

    @Override
    public long migrate(Connection sqliteConnection, MigrationContext context) throws Exception {
        String selectQuery = """
                SELECT createAt, readAt, level, identifier, title, content
                FROM alert
                """;

        long count = 0;
        long totalCount = getTotalCount(sqliteConnection);
        long lastLogged = 0;

        try (PreparedStatement ps = sqliteConnection.prepareStatement(selectQuery);
             ResultSet rs = ps.executeQuery()) {

            List<AlertEntity> batch = new ArrayList<>();

            while (rs.next()) {
                try {
                    AlertEntity entity = new AlertEntity();

                    Timestamp createAt = rs.getTimestamp("createAt");
                    Timestamp readAt = rs.getTimestamp("readAt");
                    entity.setCreateAt(createAt != null ?
                            OffsetDateTime.ofInstant(createAt.toInstant(), ZoneId.systemDefault()) : null);
                    entity.setReadAt(readAt != null ?
                            OffsetDateTime.ofInstant(readAt.toInstant(), ZoneId.systemDefault()) : null);

                    String levelStr = rs.getString("level");
                    try {
                        entity.setLevel(AlertLevel.valueOf(levelStr));
                    } catch (Exception e) {
                        log.warn("Unknown alert level: {}", levelStr);
                        entity.setLevel(AlertLevel.INFO);
                    }

                    entity.setIdentifier(rs.getString("identifier"));

                    // Parse title and content as TranslationComponent
                    String titleStr = rs.getString("title");
                    String contentStr = rs.getString("content");

                    entity.setTitle(parseTranslationComponent(titleStr));
                    entity.setContent(parseTranslationComponent(contentStr));

                    batch.add(entity);

                    if (batch.size() >= context.getBatchSize()) {
                        alertService.getBaseMapper().insertOrUpdate(batch);
                        count += batch.size();
                        batch.clear();

                        if (MigrationContext.shouldLogProgress(count, totalCount, lastLogged)) {
                            log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_PROGRESS, count, totalCount, "alert", MigrationContext.formatProgress(count, totalCount)));
                            lastLogged = count;
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to migrate alert record: {}", e.getMessage());
                }
            }

            // Save remaining records
            if (!batch.isEmpty()) {
                alertService.getBaseMapper().insertOrUpdate(batch);
                count += batch.size();
            }
        }

        log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_COMPLETED, count, "alert"));
        context.incrementTotalRecords(count);
        return count;
    }

    private long getTotalCount(Connection sqliteConnection) {
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM alert")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.warn("Failed to get total count for alert table", e);
        }
        return 0;
    }

    private TranslationComponent parseTranslationComponent(String str) {
        if (str == null || str.isEmpty()) {
            return new TranslationComponent("");
        }

        try {
            // Use JsonUtil's TranslationComponentTypeAdapter to properly deserialize
            return JsonUtil.standard().fromJson(str, TranslationComponent.class);
        } catch (Exception e) {
            log.warn("Failed to parse rule name as TranslationComponent: {}", str, e);
            return new TranslationComponent(str);
        }
    }
}
