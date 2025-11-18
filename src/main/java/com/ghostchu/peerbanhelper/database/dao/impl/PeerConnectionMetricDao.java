package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.bittorrent.peer.PeerFlag;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.PeerConnectionMetricsEntity;
import com.ghostchu.peerbanhelper.database.table.tmp.PeerConnectionMetricsTrackEntity;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.PeerConnectionMetricsDTO;
import com.j256.ormlite.support.ConnectionSource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class PeerConnectionMetricDao extends AbstractPBHDao<PeerConnectionMetricsEntity, Long> {
    public PeerConnectionMetricDao(@Autowired ConnectionSource database) throws SQLException {
        super(database, PeerConnectionMetricsEntity.class);
    }

    public List<PeerConnectionMetricsDTO> getMetricsSince(@NotNull Timestamp sinceAt, @NotNull Timestamp untilAt, @Nullable String downloader) {
        List<PeerConnectionMetricsDTO> result = new ArrayList<>();
        try {
            var where = queryBuilder().where();
            where.between("timeframeAt", sinceAt, untilAt);
            if (downloader != null && !downloader.isBlank())
                where.and().eq("downloader", downloader);
            var entities = where.query();

            // 使用Map来合并相同timeframeAt的记录
            Map<Timestamp, PeerConnectionMetricsDTO> mergedMap = new HashMap<>();

            for (PeerConnectionMetricsEntity entity : entities) {
                Timestamp timeframe = entity.getTimeframeAt();
                PeerConnectionMetricsDTO dto = PeerConnectionMetricsDTO.from(entity);

                if (mergedMap.containsKey(timeframe)) {
                    // 合并相同时间段的记录
                    PeerConnectionMetricsDTO existing = mergedMap.get(timeframe);
                    mergeMetricsDTO(existing, dto);
                } else {
                    mergedMap.put(timeframe, dto);
                }
            }
            // 将合并后的结果按时间排序
            result = new ArrayList<>(mergedMap.values());
            result.sort((a, b) -> Long.compare(b.getKey(), a.getKey()));

        } catch (SQLException e) {
            log.error("Failed to query peer connection metrics since {}", sinceAt, e);
        }
        return result;
    }


    private void mergeMetricsDTO(PeerConnectionMetricsDTO target, PeerConnectionMetricsDTO source) {
        target.setTotalConnections(target.getTotalConnections() + source.getTotalConnections());
        target.setIncomingConnections(target.getIncomingConnections() + source.getIncomingConnections());
        target.setRemoteRefuseTransferToClient(target.getRemoteRefuseTransferToClient() + source.getRemoteRefuseTransferToClient());
        target.setRemoteAcceptTransferToClient(target.getRemoteAcceptTransferToClient() + source.getRemoteAcceptTransferToClient());
        target.setLocalRefuseTransferToPeer(target.getLocalRefuseTransferToPeer() + source.getLocalRefuseTransferToPeer());
        target.setLocalAcceptTransferToPeer(target.getLocalAcceptTransferToPeer() + source.getLocalAcceptTransferToPeer());
        target.setLocalNotInterested(target.getLocalNotInterested() + source.getLocalNotInterested());
        target.setQuestionStatus(target.getQuestionStatus() + source.getQuestionStatus());
        target.setOptimisticUnchoke(target.getOptimisticUnchoke() + source.getOptimisticUnchoke());
        target.setFromDHT(target.getFromDHT() + source.getFromDHT());
        target.setFromPEX(target.getFromPEX() + source.getFromPEX());
        target.setFromLSD(target.getFromLSD() + source.getFromLSD());
        target.setFromTrackerOrOther(target.getFromTrackerOrOther() + source.getFromTrackerOrOther());
        target.setRc4Encrypted(target.getRc4Encrypted() + source.getRc4Encrypted());
        target.setPlainTextEncrypted(target.getPlainTextEncrypted() + source.getPlainTextEncrypted());
        target.setUtpSocket(target.getUtpSocket() + source.getUtpSocket());
        target.setTcpSocket(target.getTcpSocket() + source.getTcpSocket());
    }

    public synchronized void saveAggregating(List<PeerConnectionMetricsEntity> buffer, boolean overwrite){
        for (PeerConnectionMetricsEntity peerConnectionMetricsEntity : buffer) {
            try {
                var entityInDb = queryBuilder().where()
                        .eq("timeframeAt", peerConnectionMetricsEntity.getTimeframeAt())
                        .and()
                        .eq("downloader", peerConnectionMetricsEntity.getDownloader())
                        .queryForFirst();
                if (entityInDb != null) {
                    if (overwrite) {
                        peerConnectionMetricsEntity.setId(entityInDb.getId());
                    } else {
                        entityInDb.merge(peerConnectionMetricsEntity);
                    }
                } else {
                    entityInDb = peerConnectionMetricsEntity;
                }
                createOrUpdate(entityInDb);
            } catch (SQLException e) {
                log.error("Updating peer connection metrics failed for downloader {} at {}", peerConnectionMetricsEntity.getDownloader(), peerConnectionMetricsEntity.getTimeframeAt(), e);
            }
        }
    }

    public synchronized List<PeerConnectionMetricsEntity> aggregating(@NotNull List<PeerConnectionMetricsTrackEntity> fullPeerSessions) {
        List<PeerConnectionMetricsEntity> buffer = new ArrayList<>();
        for (PeerConnectionMetricsTrackEntity peerSessionEntity : fullPeerSessions) {
            var entity = findOrCreateBuffer(buffer, peerSessionEntity.getTimeframeAt(), peerSessionEntity.getDownloader());
            entity.setTotalConnections(entity.getTotalConnections() + 1);
            var flags = peerSessionEntity.getLastFlags();
            if (flags != null) {
                PeerFlag f = new PeerFlag(flags);
                if (!f.isLocalConnection())
                    entity.setIncomingConnections(entity.getIncomingConnections() + 1);
                if (f.isInteresting() && f.isRemoteChoked())
                    entity.setRemoteRefuseTransferToClient(entity.getRemoteRefuseTransferToClient() + 1);
                if (f.isInteresting() && !f.isRemoteChoked())
                    entity.setRemoteAcceptTransferToClient(entity.getRemoteAcceptTransferToClient() + 1);
                if (f.isRemoteInterested() && f.isChoked())
                    entity.setLocalRefuseTransferToPeer(entity.getLocalRefuseTransferToPeer() + 1);
                if (f.isRemoteInterested() && !f.isChoked())
                    entity.setLocalAcceptTransferToPeer(entity.getLocalAcceptTransferToPeer() + 1);
                if (!f.isRemoteChoked() && !f.isInteresting())
                    entity.setLocalNotInterested(entity.getLocalNotInterested() + 1);
                if (!f.isChoked() && !f.isRemoteInterested())
                    entity.setQuestionStatus(entity.getQuestionStatus() + 1);
                if (f.isOptimisticUnchoke())
                    entity.setOptimisticUnchoke(entity.getOptimisticUnchoke() + 1);
                if (f.isFromDHT())
                    entity.setFromDHT(entity.getFromDHT() + 1);
                else if (f.isFromPEX())
                    entity.setFromPEX(entity.getFromPEX() + 1);
                else if (f.isFromLSD())
                    entity.setFromLSD(entity.getFromLSD() + 1);
                else
                    entity.setFromTrackerOrOther(entity.getFromTrackerOrOther() + 1);
                if (f.isRc4Encrypted())
                    entity.setRc4Encrypted(entity.getRc4Encrypted() + 1);
                if (f.isPlainTextEncrypted())
                    entity.setPlainTextEncrypted(entity.getPlainTextEncrypted() + 1);
                if (f.isUtpSocket())
                    entity.setUtpSocket(entity.getUtpSocket() + 1);
                else
                    entity.setTcpSocket(entity.getTcpSocket() + 1);
            }
        }
        return buffer;
    }

    @NotNull
    private PeerConnectionMetricsEntity findOrCreateBuffer(List<PeerConnectionMetricsEntity> buffer, Timestamp timestamp, String downloader) {
        for (PeerConnectionMetricsEntity peerConnectionMetricsEntity : buffer) {
            if (peerConnectionMetricsEntity.getTimeframeAt().equals(timestamp) && peerConnectionMetricsEntity.getDownloader().equals(downloader)) {
                return peerConnectionMetricsEntity;
            }
        }
        PeerConnectionMetricsEntity entity = new PeerConnectionMetricsEntity();
        entity.setTimeframeAt(timestamp);
        entity.setDownloader(downloader);
        buffer.add(entity);
        return entity;
    }

    public void removeOutdatedData(@NotNull Timestamp beforeAt) {
        try {
            var deleteBuilder = deleteBuilder();
            deleteBuilder.where().le("timeframeAt", beforeAt);
            var deleted = deleteBuilder.delete();
            log.debug("Removed {} outdated peer connection metrics data before {}", deleted, beforeAt);
        } catch (SQLException e) {
            log.error("Failed to remove outdated peer connection metrics data before {}", beforeAt, e);
        }
    }
}
