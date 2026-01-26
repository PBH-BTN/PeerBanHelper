package com.ghostchu.peerbanhelper.databasent.migration.migrators;

import com.ghostchu.peerbanhelper.databasent.migration.MigrationContext;
import com.ghostchu.peerbanhelper.databasent.migration.TableMigrator;
import com.ghostchu.peerbanhelper.databasent.service.PeerConnectionMetricsTrackService;
import com.ghostchu.peerbanhelper.databasent.table.PeerConnectionMetricsTrackEntity;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
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
public class PeerConnectionMetricsTrackMigrator implements TableMigrator {
    private final PeerConnectionMetricsTrackService service;

    public PeerConnectionMetricsTrackMigrator(PeerConnectionMetricsTrackService service) {
        this.service = service;
    }

    @Override
    public String getTableName() {
        return "peer_connection_metrics_track";
    }

    @Override
    public int getMigrationOrder() {
        return 101;
    }

    @Override
    public boolean isTableAvailable(Connection sqliteConnection) throws Exception {
        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='peer_connection_metrics_track'";
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery(query)) {
            return rs.next();
        }
    }

    @Override
    public long migrate(Connection sqliteConnection, MigrationContext context) throws Exception {
        String selectQuery = """
                SELECT timeframeAt, downloader, torrent_id, address, port,
                       peerId, clientName, lastFlags
                FROM peer_connection_metrics_track
                """;

        long count = 0;
        long totalCount = getTotalCount(sqliteConnection);
        long lastLogged = 0;

        try (PreparedStatement ps = sqliteConnection.prepareStatement(selectQuery);
             ResultSet rs = ps.executeQuery()) {

            List<PeerConnectionMetricsTrackEntity> batch = new ArrayList<>();

            while (rs.next()) {
                try {
                    PeerConnectionMetricsTrackEntity entity = new PeerConnectionMetricsTrackEntity();

                    Timestamp timeframeAt = rs.getTimestamp("timeframeAt");
                    entity.setTimeframeAt(timeframeAt != null ?
                            OffsetDateTime.ofInstant(timeframeAt.toInstant(), ZoneId.systemDefault()) : null);

                    entity.setDownloader(rs.getString("downloader"));
                    entity.setTorrentId(rs.getLong("torrent_id"));

                    String addressStr = rs.getString("address");
                    entity.setAddress(InetAddress.getByName(addressStr));
                    entity.setPort(rs.getInt("port"));
                    entity.setPeerId(rs.getString("peerId"));
                    entity.setClientName(rs.getString("clientName"));
                    entity.setLastFlags(rs.getString("lastFlags"));

                    batch.add(entity);

                    if (batch.size() >= context.getBatchSize()) {
                        service.getBaseMapper().insertOrUpdate(batch);
                        count += batch.size();
                        batch.clear();

                        if (MigrationContext.shouldLogProgress(count, totalCount, lastLogged)) {
                            log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_PROGRESS, count, totalCount, "peer_connection_metrics_track", MigrationContext.formatProgress(count, totalCount)));

                            lastLogged = count;
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to migrate peer_connection_metrics_track record: {}", e.getMessage());
                }
            }

            if (!batch.isEmpty()) {
                service.getBaseMapper().insertOrUpdate(batch);
                count += batch.size();
            }
        }

        log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_COMPLETED, count, "peer_connection_metrics_track"));
        context.incrementTotalRecords(count);
        return count;
    }

    private long getTotalCount(Connection sqliteConnection) {
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM peer_connection_metrics_track")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.warn("Failed to get total count for peer_connection_metrics_track table", e);
        }
        return 0;
    }
}
