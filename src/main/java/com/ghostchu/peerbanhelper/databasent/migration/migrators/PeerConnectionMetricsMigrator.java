package com.ghostchu.peerbanhelper.databasent.migration.migrators;

import com.ghostchu.peerbanhelper.databasent.migration.MigrationContext;
import com.ghostchu.peerbanhelper.databasent.migration.TableMigrator;
import com.ghostchu.peerbanhelper.databasent.service.PeerConnectionMetricsService;
import com.ghostchu.peerbanhelper.databasent.table.PeerConnectionMetricsEntity;
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
public class PeerConnectionMetricsMigrator implements TableMigrator {
    private final PeerConnectionMetricsService peerConnectionMetricsService;

    public PeerConnectionMetricsMigrator(PeerConnectionMetricsService peerConnectionMetricsService) {
        this.peerConnectionMetricsService = peerConnectionMetricsService;
    }

    @Override
    public String getTableName() {
        return "peer_connection_metrics";
    }

    @Override
    public boolean isTableAvailable(Connection sqliteConnection) throws Exception {
        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='peer_connection_metrics'";
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery(query)) {
            return rs.next();
        }
    }

    @Override
    public long migrate(Connection sqliteConnection, MigrationContext context) throws Exception {
        String selectQuery = """
                SELECT timeframeAt, downloader, totalConnections, incomingConnections,
                       remoteRefuseTransferToClient, remoteAcceptTransferToClient,
                       localRefuseTransferToPeer, localAcceptTransferToPeer, localNotInterested,
                       questionStatus, optimisticUnchoke, fromDHT, fromPEX, fromLSD,
                       fromTrackerOrOther, rc4Encrypted, plainTextEncrypted, utpSocket, tcpSocket
                FROM peer_connection_metrics
                """;

        long count = 0;
        long totalCount = getTotalCount(sqliteConnection);
        long lastLogged = 0;

        try (PreparedStatement ps = sqliteConnection.prepareStatement(selectQuery);
             ResultSet rs = ps.executeQuery()) {

            List<PeerConnectionMetricsEntity> batch = new ArrayList<>();

            while (rs.next()) {
                PeerConnectionMetricsEntity entity = new PeerConnectionMetricsEntity();

                Timestamp timeframeAt = rs.getTimestamp("timeframeAt");
                entity.setTimeframeAt(timeframeAt != null ?
                        OffsetDateTime.ofInstant(timeframeAt.toInstant(), ZoneId.systemDefault()) : null);

                entity.setDownloader(rs.getString("downloader"));
                entity.setTotalConnections(rs.getLong("totalConnections"));
                entity.setIncomingConnections(rs.getLong("incomingConnections"));
                entity.setRemoteRefuseTransferToClient(rs.getLong("remoteRefuseTransferToClient"));
                entity.setRemoteAcceptTransferToClient(rs.getLong("remoteAcceptTransferToClient"));
                entity.setLocalRefuseTransferToPeer(rs.getLong("localRefuseTransferToPeer"));
                entity.setLocalAcceptTransferToPeer(rs.getLong("localAcceptTransferToPeer"));
                entity.setLocalNotInterested(rs.getLong("localNotInterested"));
                entity.setQuestionStatus(rs.getLong("questionStatus"));
                entity.setOptimisticUnchoke(rs.getLong("optimisticUnchoke"));
                entity.setFromDHT(rs.getLong("fromDHT"));
                entity.setFromPEX(rs.getLong("fromPEX"));
                entity.setFromLSD(rs.getLong("fromLSD"));
                entity.setFromTrackerOrOther(rs.getLong("fromTrackerOrOther"));
                entity.setRc4Encrypted(rs.getLong("rc4Encrypted"));
                entity.setPlainTextEncrypted(rs.getLong("plainTextEncrypted"));
                entity.setUtpSocket(rs.getLong("utpSocket"));
                entity.setTcpSocket(rs.getLong("tcpSocket"));

                batch.add(entity);

                if (batch.size() >= context.getBatchSize()) {
                    peerConnectionMetricsService.getBaseMapper().insertOrUpdate(batch);
                    count += batch.size();
                    batch.clear();

                    if (MigrationContext.shouldLogProgress(count, totalCount, lastLogged)) {
                        log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_PROGRESS, count, totalCount, "peer_connection_metrics", MigrationContext.formatProgress(count, totalCount)));
                        lastLogged = count;
                    }
                }
            }

            if (!batch.isEmpty()) {
                peerConnectionMetricsService.getBaseMapper().insertOrUpdate(batch);
                count += batch.size();
            }
        }

        log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_COMPLETED, count, "peer_connection_metrics"));
        context.incrementTotalRecords(count);
        return count;
    }

    private long getTotalCount(Connection sqliteConnection) {
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM peer_connection_metrics")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.warn("Failed to get total count for peer_connection_metrics table", e);
        }
        return 0;
    }
}
