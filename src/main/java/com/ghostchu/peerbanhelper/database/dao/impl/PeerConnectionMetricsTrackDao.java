package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.PeerConnectionMetricsTrackEntity;
import com.ghostchu.peerbanhelper.database.table.TorrentEntity;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.j256.ormlite.support.ConnectionSource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
@Slf4j
public final class PeerConnectionMetricsTrackDao extends AbstractPBHDao<PeerConnectionMetricsTrackEntity, Long> {

    public PeerConnectionMetricsTrackDao(@Autowired ConnectionSource database) throws SQLException {
        super(database, PeerConnectionMetricsTrackEntity.class);
    }

    public void upsertPeerSession(@NotNull Downloader downloader, @NotNull TorrentEntity torrent, @NotNull Collection<Peer> peers) {
        long startOfDay = MiscUtil.getStartOfToday(System.currentTimeMillis());
        Timestamp startOfDayTs = new Timestamp(startOfDay);
        try {
            List<PeerConnectionMetricsTrackEntity> entities = new ArrayList<>();
            for (Peer peer : peers) {
                PeerConnectionMetricsTrackEntity entity = queryBuilder().where()
                        .eq("timeframeAt", startOfDay)
                        .and()
                        .eq("downloader", downloader.getId())
                        .and()
                        .eq("torrent_id", torrent)
                        .and()
                        .eq("address", peer.getPeerAddress().getAddress().toNormalizedString())
                        .and()
                        .eq("port", peer.getPeerAddress().getPort())
                        .queryForFirst();
                if (entity == null) {
                    entity = new PeerConnectionMetricsTrackEntity();
                    entity.setTimeframeAt(startOfDayTs);
                    entity.setDownloader(downloader.getId());
                    entity.setTorrent(torrent);
                    entity.setAddress(peer.getPeerAddress().getAddress().toNormalizedString());
                    entity.setPort(peer.getPeerAddress().getPort());
                }
                entity.setPeerId(peer.getPeerId());
                entity.setClientName(peer.getClientName());
                entity.setLastFlags(peer.getFlags() == null ? null : peer.getFlags().getLtStdString());
                entities.add(entity);
            }
            callBatchTasks(() -> {
                for (PeerConnectionMetricsTrackEntity entity : entities) {
                    try {
                        createOrUpdate(entity);
                    } catch (SQLException sqle) {
                        log.warn("Failed to update or create peer session entity {}", entity, sqle);
                    }
                }
                return null;
            });
        } catch (SQLException e) {
            log.error("Failed to upsert peer sessions", e);
        }
    }

}
