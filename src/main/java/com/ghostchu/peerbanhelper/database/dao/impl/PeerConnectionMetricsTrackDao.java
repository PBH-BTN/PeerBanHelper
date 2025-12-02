package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.PeerConnectionMetricsTrackEntity;
import com.ghostchu.peerbanhelper.database.table.TorrentEntity;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.j256.ormlite.support.ConnectionSource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public final class PeerConnectionMetricsTrackDao extends AbstractPBHDao<PeerConnectionMetricsTrackEntity, Long> {

    private final Cache<@NotNull CacheKey, @NotNull PeerConnectionMetricsTrackEntity> cache = CacheBuilder.newBuilder()
            .maximumSize(ExternalSwitch.parseInt("pbh.module.session-analyse-service-module.cache-size", 1000))
            .expireAfterAccess(3, TimeUnit.MINUTES)
            .removalListener((RemovalListener<@NotNull CacheKey, @NotNull PeerConnectionMetricsTrackEntity>) notification -> {
                var v = notification.getValue();
                try {
                    createOrUpdate(v);
                } catch (SQLException e) {
                    log.error("Unable flush back to database for sessionAnalyseServiceModule {}", v, e);
                }
            })
            .softValues()
            .build();

    public PeerConnectionMetricsTrackDao(@Autowired ConnectionSource database) throws SQLException {
        super(database, PeerConnectionMetricsTrackEntity.class);
    }

    public void flushAll() {
        try {
            callBatchTasks(() -> {
                for (PeerConnectionMetricsTrackEntity value : cache.asMap().values()) {
                    try {
                        createOrUpdate(value);
                    } catch (SQLException e) {
                        log.error("Unable flush back to database for PeerConnectionMetricsTrack: {}", value, e);
                    }
                }
                return null;
            });
        } catch (SQLException e) {
            log.error("Unable to flush all tracked swarm cache to database", e);
        }
    }

    public void syncPeers(@NotNull Downloader downloader, @NotNull Torrent torrent, @NotNull List<Peer> peers, TorrentDao torrentDao) throws SQLException, ExecutionException {
        for (Peer peer : peers) {
            if (peer.isHandshaking()) continue;
            TorrentEntity torrentEntity = torrentDao.createIfNotExists(new TorrentEntity(
                    null,
                    torrent.getHash(),
                    torrent.getName(),
                    torrent.getSize(),
                    torrent.isPrivate()
            ));
            CacheKey cacheKey = new CacheKey(
                    new Timestamp(MiscUtil.getStartOfToday(System.currentTimeMillis())),
                    downloader.getId(),
                    torrentEntity.getId(),
                    peer.getPeerAddress().getAddress().toNormalizedString(),
                    peer.getPeerAddress().getPort()
            );
            PeerConnectionMetricsTrackEntity trackEntity = cache.get(cacheKey, () -> {
                PeerConnectionMetricsTrackEntity entity = queryBuilder().where()
                        .eq("timeframeAt", cacheKey.timeframeAt)
                        .and()
                        .eq("downloader", cacheKey.downloader)
                        .and()
                        .eq("torrent_id", cacheKey.torrentId)
                        .and()
                        .eq("address", cacheKey.address)
                        .and()
                        .eq("port", cacheKey.port)
                        .queryForFirst();
                if (entity == null) {
                    entity = new PeerConnectionMetricsTrackEntity();
                    entity.setTimeframeAt(cacheKey.timeframeAt);
                    entity.setDownloader(downloader.getId());
                    entity.setTorrent(torrentEntity);
                    entity.setAddress(peer.getPeerAddress().getAddress().toNormalizedString());
                    entity.setPort(peer.getPeerAddress().getPort());
                }
                return entity;
            });
            trackEntity.setPeerId(peer.getPeerId());
            trackEntity.setClientName(peer.getClientName());
            trackEntity.setLastFlags(peer.getFlags() == null ? null : peer.getFlags().getLtStdString());
        }
    }

    public record CacheKey(Timestamp timeframeAt, String downloader, long torrentId, String address, int port) {
    }
}
