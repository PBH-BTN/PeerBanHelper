package com.ghostchu.peerbanhelper.databasent.migration.migrators;

import com.ghostchu.peerbanhelper.databasent.migration.MigrationContext;
import com.ghostchu.peerbanhelper.databasent.migration.TableMigrator;
import com.ghostchu.peerbanhelper.databasent.service.PeerRecordService;
import com.ghostchu.peerbanhelper.databasent.table.PeerRecordEntity;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.ipdb.IPGeoData;
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

/**
 * Migrates peer_records table
 */
@Slf4j
public class PeerRecordMigrator implements TableMigrator {
    private final PeerRecordService peerRecordService;

    public PeerRecordMigrator(PeerRecordService peerRecordService) {
        this.peerRecordService = peerRecordService;
    }

    @Override
    public String getTableName() {
        return "peer_records";
    }

    @Override
    public int getMigrationOrder() {
        return 60;
    }

    @Override
    public boolean isTableAvailable(Connection sqliteConnection) throws Exception {
        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='peer_records'";
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery(query)) {
            return rs.next();
        }
    }

    @Override
    public long migrate(Connection sqliteConnection, MigrationContext context) throws Exception {
        String selectQuery = """
                SELECT address, port, torrent_id, downloader, peerId, clientName,
                       uploaded, uploadedOffset, uploadSpeed, downloaded, downloadedOffset, downloadSpeed,
                       lastFlags, firstTimeSeen, lastTimeSeen
                FROM peer_records
                ORDER BY id
                """;

        long count = 0;
        long totalCount = getTotalCount(sqliteConnection);

        try (PreparedStatement ps = sqliteConnection.prepareStatement(selectQuery);
             ResultSet rs = ps.executeQuery()) {

            List<PeerRecordEntity> batch = new ArrayList<>();

            while (rs.next()) {
                try {
                    PeerRecordEntity entity = mapResultSetToEntity(rs, context);
                    batch.add(entity);

                    if (batch.size() >= context.getBatchSize()) {
                        saveBatch(batch);
                        count += batch.size();
                        batch.clear();
                        log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_PROGRESS, count, totalCount, "peer_records", MigrationContext.formatProgress(count, totalCount)));

                    }
                } catch (Exception e) {
                    log.error("Failed to migrate peer_record: {}", e.getMessage());
                }
            }

            if (!batch.isEmpty()) {
                saveBatch(batch);
                count += batch.size();
            }
        }

        log.info(tlUI(Lang.DBNT_MIGRATOR_MIGRATING_COMPLETED, count, "peer_records"));
        context.incrementTotalRecords(count);
        return count;
    }

    private long getTotalCount(Connection sqliteConnection) {
        try (var stmt = sqliteConnection.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM peer_records")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.warn("Failed to get total count for peer_records table", e);
        }
        return 0;
    }

    private PeerRecordEntity mapResultSetToEntity(ResultSet rs, MigrationContext context) throws Exception {
        PeerRecordEntity entity = new PeerRecordEntity();

        String addressStr = rs.getString("address");
        InetAddress address = InetAddress.getByName(addressStr);
        entity.setAddress(address);
        entity.setPort(rs.getInt("port"));
        entity.setTorrentId(rs.getLong("torrent_id"));
        entity.setDownloader(rs.getString("downloader"));
        entity.setPeerId(rs.getString("peerId"));
        entity.setClientName(rs.getString("clientName"));
        entity.setUploaded(rs.getLong("uploaded"));
        entity.setUploadedOffset(rs.getLong("uploadedOffset"));
        entity.setUploadSpeed(rs.getLong("uploadSpeed"));
        entity.setDownloaded(rs.getLong("downloaded"));
        entity.setDownloadedOffset(rs.getLong("downloadedOffset"));
        entity.setDownloadSpeed(rs.getLong("downloadSpeed"));
        entity.setLastFlags(rs.getString("lastFlags"));

        Timestamp firstTimeSeen = rs.getTimestamp("firstTimeSeen");
        Timestamp lastTimeSeen = rs.getTimestamp("lastTimeSeen");
        entity.setFirstTimeSeen(firstTimeSeen != null ?
                OffsetDateTime.ofInstant(firstTimeSeen.toInstant(), ZoneId.systemDefault()) : null);
        entity.setLastTimeSeen(lastTimeSeen != null ?
                OffsetDateTime.ofInstant(lastTimeSeen.toInstant(), ZoneId.systemDefault()) : null);

        // Generate GeoIP data
        if (!context.isSkipGeoIP() && context.getIpdbManager() != null) {
            try {
                var ipdbResponse = context.getIpdbManager().queryIPDB(address);
                if (ipdbResponse != null && ipdbResponse.geoData() != null) {
                    IPGeoData geoData = ipdbResponse.geoData().get();
                    entity.setPeerGeoIp(geoData);
                }
            } catch (Exception e) {
                log.debug("Failed to generate GeoIP for IP {}: {}", addressStr, e.getMessage());
            }
        }

        return entity;
    }


    private void saveBatch(List<PeerRecordEntity> batch) {
        peerRecordService.getBaseMapper().insertOrUpdate(batch);
    }
}
