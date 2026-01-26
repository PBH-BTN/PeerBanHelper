package com.ghostchu.peerbanhelper.databasent.migration.migrators;

import com.ghostchu.peerbanhelper.databasent.migration.MigrationContext;
import com.ghostchu.peerbanhelper.databasent.migration.TableMigrator;
import com.ghostchu.peerbanhelper.databasent.service.HistoryService;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.common.reflect.TypeToken;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

/**
 * Migrates history (ban records) table
 * Handles complex field transformations:
 * - Timestamp -> OffsetDateTime
 * - VARCHAR IP -> InetAddress
 * - rule_id + module_id -> module_name + rule_name (needs JOIN)
 * - Generates GeoIP data for each IP
 */
@Slf4j
public class HistoryMigrator implements TableMigrator {
    private final HistoryService historyService;
    private final TorrentService torrentService;

    public HistoryMigrator(HistoryService historyService, TorrentService torrentService) {
        this.historyService = historyService;
        this.torrentService = torrentService;
    }

    @Override
    public String getTableName() {
        return "history";
    }

    @Override
    public int getMigrationOrder() {
        return 50; // After torrents, modules, rules
    }

    @Override
    public boolean isTableAvailable(Connection sqliteConnection) throws Exception {
        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='history'";
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery(query)) {
            return rs.next();
        }
    }

    @Override
    public long migrate(Connection sqliteConnection, MigrationContext context) throws Exception {
        // Query with JOIN to get module and rule names
        String selectQuery = """
                SELECT 
                    h.id, h.banAt, h.unbanAt, h.ip, h.port, h.peerId, h.peerClientName,
                    h.peerUploaded, h.peerDownloaded, h.peerProgress, h.downloaderProgress,
                    h.torrent_id, h.rule_id, h.description, h.flags, h.downloader,
                    h.structuredData,
                    m.name as module_name,
                    r.rule as rule_name
                FROM history h
                LEFT JOIN rules r ON h.rule_id = r.id
                LEFT JOIN modules m ON r.module_id = m.id
                ORDER BY h.id
                """;

        long count = 0;
        long totalCount = getTotalCount(sqliteConnection);

        try (PreparedStatement ps = sqliteConnection.prepareStatement(selectQuery);
             ResultSet rs = ps.executeQuery()) {

            List<HistoryEntity> batch = new ArrayList<>();

            while (rs.next()) {
                try {
                    HistoryEntity entity = mapResultSetToEntity(rs, context);
                    batch.add(entity);

                    if (batch.size() >= context.getBatchSize()) {
                        saveBatch(batch);
                        count += batch.size();
                        batch.clear();
                        log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_PROGRESS, count, totalCount, "history", MigrationContext.formatProgress(count, totalCount)));
                    }
                } catch (Exception e) {
                    log.error("Failed to migrate history record at row: {}", rs.getLong("id"), e);
                }
            }

            // Save remaining records
            if (!batch.isEmpty()) {
                saveBatch(batch);
                count += batch.size();
            }
        }

        log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_COMPLETED, count, "history"));
        context.incrementTotalRecords(count);
        return count;
    }

    private long getTotalCount(Connection sqliteConnection) {
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM history")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.warn("Failed to get total count for history table", e);
        }
        return 0;
    }

    private HistoryEntity mapResultSetToEntity(ResultSet rs, MigrationContext context) throws Exception {
        HistoryEntity entity = new HistoryEntity();

        // Basic fields
        Timestamp banAt = rs.getTimestamp("banAt");
        Timestamp unbanAt = rs.getTimestamp("unbanAt");
        entity.setBanAt(banAt != null ? OffsetDateTime.ofInstant(banAt.toInstant(), ZoneId.systemDefault()) : null);
        entity.setUnbanAt(unbanAt != null ? OffsetDateTime.ofInstant(unbanAt.toInstant(), ZoneId.systemDefault()) : null);

        String ipStr = rs.getString("ip");
        InetAddress ip = InetAddress.getByName(ipStr);
        entity.setIp(ip);
        entity.setPort(rs.getInt("port"));
        entity.setPeerId(rs.getString("peerId"));
        entity.setPeerClientName(rs.getString("peerClientName"));

        // Numeric fields
        Long peerUploaded = rs.getObject("peerUploaded") != null ? rs.getLong("peerUploaded") : null;
        Long peerDownloaded = rs.getObject("peerDownloaded") != null ? rs.getLong("peerDownloaded") : null;
        entity.setPeerUploaded(peerUploaded);
        entity.setPeerDownloaded(peerDownloaded);
        entity.setPeerProgress(rs.getDouble("peerProgress"));

        Double downloaderProgress = rs.getObject("downloaderProgress") != null ? rs.getDouble("downloaderProgress") : null;
        entity.setDownloaderProgress(downloaderProgress);

        // Torrent reference
        long oldTorrentId = rs.getLong("torrent_id");
        Long newTorrentId = findTorrentId(oldTorrentId, context);
        entity.setTorrentId(newTorrentId);

        // Module and rule names (from JOIN)
        String moduleName = rs.getString("module_name");
        String ruleNameStr = rs.getString("rule_name");
        entity.setModuleName(moduleName);

        // Parse rule name as TranslationComponent
        if (ruleNameStr != null && !ruleNameStr.isEmpty()) {
            try {
                JsonObject ruleJson = JsonParser.parseString(ruleNameStr).getAsJsonObject();
                TranslationComponent ruleName = new TranslationComponent(
                        ruleJson.get("key").getAsString(),
                        ruleJson.has("args") ? ruleJson.get("args").toString() : null
                );
                entity.setRuleName(ruleName);
            } catch (Exception e) {
                log.warn("Failed to parse rule name as TranslationComponent: {}", ruleNameStr);
                entity.setRuleName(new TranslationComponent(ruleNameStr));
            }
        }

        // Description
        String descriptionStr = rs.getString("description");
        if (descriptionStr != null && !descriptionStr.isEmpty()) {
            try {
                JsonObject descJson = JsonParser.parseString(descriptionStr).getAsJsonObject();
                TranslationComponent description = new TranslationComponent(
                        descJson.get("key").getAsString(),
                        descJson.has("args") ? descJson.get("args").toString() : null
                );
                entity.setDescription(description);
            } catch (Exception e) {
                log.warn("Failed to parse description as TranslationComponent: {}", descriptionStr);
                entity.setDescription(new TranslationComponent(descriptionStr));
            }
        }

        entity.setFlags(rs.getString("flags"));
        entity.setDownloader(rs.getString("downloader"));

        // Structured data - parse JSON string to Map
        String structuredDataStr = rs.getString("structuredData");
        if (structuredDataStr != null && !structuredDataStr.isEmpty() && !"{}".equals(structuredDataStr)) {
            try {
                // Use JsonUtil to properly deserialize to Map<String, Object>
                Map<String, Object> structuredData = JsonUtil.standard().fromJson(
                        structuredDataStr,
                        new TypeToken<@NotNull Map<String, Object>>() {
                        }.getType()
                );
                entity.setStructuredData(structuredData);
            } catch (Exception e) {
                log.warn("Failed to parse structured data: {}", structuredDataStr, e);
                // Set empty map as fallback
                entity.setStructuredData(new HashMap<>());
            }
        }

        // Generate GeoIP data
        if (!context.isSkipGeoIP() && context.getIpdbManager() != null) {
            try {
                var ipdbResponse = context.getIpdbManager().queryIPDB(ip);
                if (ipdbResponse != null && ipdbResponse.geoData() != null) {
                    IPGeoData geoData = ipdbResponse.geoData().get();
                    entity.setPeerGeoIp(geoData);
                }
            } catch (Exception e) {
                log.debug("Failed to generate GeoIP for IP {}: {}", ipStr, e.getMessage());
            }
        }

        return entity;
    }

    private Long findTorrentId(long oldTorrentId, MigrationContext context) {
        // Check cache first
        if (context.getTorrentIdMap().containsKey(oldTorrentId)) {
            return context.getTorrentIdMap().get(oldTorrentId);
        }

        // Query from new DB - need to find by some unique field
        // This is a simplified version - in production we'd need better ID mapping
        return oldTorrentId; // Fallback - may need adjustment
    }

    private void saveBatch(List<HistoryEntity> batch) {
        // Use MyBatis-Plus batch insert for better performance
        historyService.getBaseMapper().insertOrUpdate(batch);
    }
}
