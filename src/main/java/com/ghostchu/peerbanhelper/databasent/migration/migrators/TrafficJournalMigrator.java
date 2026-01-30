package com.ghostchu.peerbanhelper.databasent.migration.migrators;

import com.ghostchu.peerbanhelper.databasent.migration.MigrationContext;
import com.ghostchu.peerbanhelper.databasent.migration.TableMigrator;
import com.ghostchu.peerbanhelper.databasent.service.TrafficJournalService;
import com.ghostchu.peerbanhelper.databasent.table.TrafficJournalEntity;
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

/**
 * Migrates traffic_journal_v3 table
 */
@Slf4j
public class TrafficJournalMigrator implements TableMigrator {
    private final TrafficJournalService trafficJournalService;

    public TrafficJournalMigrator(TrafficJournalService trafficJournalService) {
        this.trafficJournalService = trafficJournalService;
    }

    @Override
    public String getTableName() {
        return "traffic_journal_v3";
    }

    @Override
    public int getMigrationOrder() {
        return 70;
    }

    @Override
    public boolean isTableAvailable(Connection sqliteConnection) throws Exception {
        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='traffic_journal_v3'";
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery(query)) {
            return rs.next();
        }
    }

    @Override
    public long migrate(Connection sqliteConnection, MigrationContext context) throws Exception {
        String selectQuery = """
                SELECT timestamp, downloader, dataOverallUploadedAtStart, dataOverallUploaded,
                       dataOverallDownloadedAtStart, dataOverallDownloaded, protocolOverallUploadedAtStart,
                       protocolOverallUploaded, protocolOverallDownloadedAtStart, protocolOverallDownloaded
                FROM traffic_journal_v3
                ORDER BY id
                """;

        long count = 0;
        long totalCount = getTotalCount(sqliteConnection);
        long lastLogged = 0;

        try (PreparedStatement ps = sqliteConnection.prepareStatement(selectQuery);
             ResultSet rs = ps.executeQuery()) {

            List<TrafficJournalEntity> batch = new ArrayList<>();

            while (rs.next()) {
                try {
                    TrafficJournalEntity entity = new TrafficJournalEntity();

                    // timestamp is stored as BIGINT (epoch millis)
                    long timestampMillis = rs.getLong("timestamp");
                    entity.setTimestamp(OffsetDateTime.ofInstant(
                            Instant.ofEpochMilli(timestampMillis), ZoneId.systemDefault()));

                    entity.setDownloader(rs.getString("downloader"));
                    entity.setDataOverallUploadedAtStart(rs.getLong("dataOverallUploadedAtStart"));
                    entity.setDataOverallUploaded(rs.getLong("dataOverallUploaded"));
                    entity.setDataOverallDownloadedAtStart(rs.getLong("dataOverallDownloadedAtStart"));
                    entity.setDataOverallDownloaded(rs.getLong("dataOverallDownloaded"));
                    entity.setProtocolOverallUploadedAtStart(rs.getLong("protocolOverallUploadedAtStart"));
                    entity.setProtocolOverallUploaded(rs.getLong("protocolOverallUploaded"));
                    entity.setProtocolOverallDownloadedAtStart(rs.getLong("protocolOverallDownloadedAtStart"));
                    entity.setProtocolOverallDownloaded(rs.getLong("protocolOverallDownloaded"));

                    batch.add(entity);

                    if (batch.size() >= context.getBatchSize()) {
                        trafficJournalService.getBaseMapper().insertOrUpdate(batch);
                        count += batch.size();
                        batch.clear();

                        if (MigrationContext.shouldLogProgress(count, totalCount, lastLogged)) {
                            log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_PROGRESS, count, totalCount, "traffic_journal_v3", MigrationContext.formatProgress(count, totalCount)));

                            lastLogged = count;
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to migrate traffic_journal record: {}", e.getMessage());
                }
            }

            if (!batch.isEmpty()) {
                trafficJournalService.getBaseMapper().insertOrUpdate(batch);
                count += batch.size();
            }
        }

        log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_COMPLETED, count, "traffic_journal_v3"));
        context.incrementTotalRecords(count);
        return count;
    }

    private long getTotalCount(Connection sqliteConnection) {
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM traffic_journal_v3")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.warn("Failed to get total count for traffic_journal_v3 table", e);
        }
        return 0;
    }
}
