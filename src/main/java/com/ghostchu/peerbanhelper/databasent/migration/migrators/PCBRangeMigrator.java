package com.ghostchu.peerbanhelper.databasent.migration.migrators;

import com.ghostchu.peerbanhelper.databasent.migration.MigrationContext;
import com.ghostchu.peerbanhelper.databasent.migration.TableMigrator;
import com.ghostchu.peerbanhelper.databasent.service.PCBRangeService;
import com.ghostchu.peerbanhelper.databasent.table.PCBRangeEntity;
import com.ghostchu.peerbanhelper.text.Lang;
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

@Slf4j
public class PCBRangeMigrator implements TableMigrator {
    private final PCBRangeService pcbRangeService;

    public PCBRangeMigrator(PCBRangeService pcbRangeService) {
        this.pcbRangeService = pcbRangeService;
    }

    @Override
    public String getTableName() {
        return "pcb_range";
    }

    @Override
    public int getMigrationOrder() {
        return 91;
    }

    @Override
    public boolean isTableAvailable(Connection sqliteConnection) throws Exception {
        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='pcb_range'";
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery(query)) {
            return rs.next();
        }
    }

    @Override
    public long migrate(Connection sqliteConnection, MigrationContext context) throws Exception {
        String selectQuery = """
                SELECT range, torrentId, lastReportProgress, lastReportUploaded,
                       trackingUploadedIncreaseTotal, rewindCounter, progressDifferenceCounter,
                       firstTimeSeen, lastTimeSeen, downloader, banDelayWindowEndAt,
                       fastPcbTestExecuteAt, lastTorrentCompletedSize
                FROM pcb_range
                """;

        long count = 0;
        long totalCount = getTotalCount(sqliteConnection);
        long lastLogged = 0;

        try (PreparedStatement ps = sqliteConnection.prepareStatement(selectQuery);
             ResultSet rs = ps.executeQuery()) {

            List<PCBRangeEntity> batch = new ArrayList<>();

            while (rs.next()) {
                try {
                    PCBRangeEntity entity = new PCBRangeEntity();

                    entity.setRange(rs.getString("range"));
                    entity.setTorrentId(rs.getString("torrentId"));
                    entity.setLastReportProgress(rs.getDouble("lastReportProgress"));
                    entity.setLastReportUploaded(rs.getLong("lastReportUploaded"));
                    entity.setTrackingUploadedIncreaseTotal(rs.getLong("trackingUploadedIncreaseTotal"));
                    entity.setRewindCounter(rs.getInt("rewindCounter"));
                    entity.setProgressDifferenceCounter(rs.getInt("progressDifferenceCounter"));

                    Timestamp firstTimeSeen = rs.getTimestamp("firstTimeSeen");
                    Timestamp lastTimeSeen = rs.getTimestamp("lastTimeSeen");
                    Timestamp banDelayWindowEndAt = rs.getTimestamp("banDelayWindowEndAt");

                    entity.setFirstTimeSeen(firstTimeSeen != null ?
                            OffsetDateTime.ofInstant(firstTimeSeen.toInstant(), ZoneId.systemDefault()) : null);
                    entity.setLastTimeSeen(lastTimeSeen != null ?
                            OffsetDateTime.ofInstant(lastTimeSeen.toInstant(), ZoneId.systemDefault()) : null);
                    entity.setBanDelayWindowEndAt(banDelayWindowEndAt != null ?
                            OffsetDateTime.ofInstant(banDelayWindowEndAt.toInstant(), ZoneId.systemDefault()) : null);

                    entity.setDownloader(rs.getString("downloader"));

                    // Convert BIGINT (epoch millis) to OffsetDateTime for fastPcbTestExecuteAt
                    long fastPcbTestExecuteAtMillis = rs.getLong("fastPcbTestExecuteAt");
                    entity.setFastPcbTestExecuteAt(OffsetDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(fastPcbTestExecuteAtMillis),
                            ZoneId.systemDefault()));

                    // lastTorrentCompletedSize is a size in bytes, not a timestamp
                    entity.setLastTorrentCompletedSize(rs.getLong("lastTorrentCompletedSize"));

                    batch.add(entity);

                    if (batch.size() >= context.getBatchSize()) {
                        pcbRangeService.getBaseMapper().insertOrUpdate(batch);
                        count += batch.size();
                        batch.clear();

                        if (MigrationContext.shouldLogProgress(count, totalCount, lastLogged)) {
                            log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_PROGRESS, count, totalCount, "pcb_range", MigrationContext.formatProgress(count, totalCount)));
                            lastLogged = count;
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to migrate pcb_range record: {}", e.getMessage());
                }
            }

            if (!batch.isEmpty()) {
                pcbRangeService.getBaseMapper().insertOrUpdate(batch);
                count += batch.size();
            }
        }

        log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_COMPLETED, count, "pcb_range"));
        context.incrementTotalRecords(count);
        return count;
    }

    private long getTotalCount(Connection sqliteConnection) {
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM pcb_range")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.warn("Failed to get total count for pcb_range table", e);
        }
        return 0;
    }
}
