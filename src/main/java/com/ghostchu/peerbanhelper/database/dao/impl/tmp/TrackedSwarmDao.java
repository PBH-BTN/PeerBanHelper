package com.ghostchu.peerbanhelper.database.dao.impl.tmp;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.tmp.TrackedSwarmEntity;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.util.query.Page;
import com.ghostchu.peerbanhelper.util.query.Pageable;
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
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public final class TrackedSwarmDao extends AbstractPBHDao<TrackedSwarmEntity, Long> {
    private final Cache<@NotNull CacheKey, @NotNull TrackedSwarmEntity> cache = CacheBuilder.newBuilder()
            .maximumSize(ExternalSwitch.parseInt("pbh.module.swarm-tracking-module.cache-size", 1000))
            .expireAfterAccess(3, TimeUnit.MINUTES)
            .removalListener((RemovalListener<@NotNull CacheKey, @NotNull TrackedSwarmEntity>) notification -> {
                var v = notification.getValue();
                try {
                    createOrUpdate(v);
                } catch (SQLException e) {
                    log.error("Unable flush back to database for swarm tracking {}", v, e);
                }
            })
            .softValues()
            .build();

    public TrackedSwarmDao(@Autowired ConnectionSource source) throws SQLException {
        super(source, TrackedSwarmEntity.class);
    }

    public Page<TrackedSwarmEntity> getPendingSubmitTrackedPeers(Pageable pageable, long idAfterThan) throws SQLException {
        var queryBuilder = queryBuilder().where()
                .gt("id", idAfterThan)
                .queryBuilder()
                .orderBy("id", true);
        return queryByPaging(queryBuilder, pageable);
    }

    public void syncPeers(@NotNull Downloader downloader, @NotNull Torrent torrent, Peer peer) throws ExecutionException {
        CacheKey cacheKey = new CacheKey(
                peer.getPeerAddress().getAddress().toNormalizedString(),
                peer.getPeerAddress().getPort(),
                torrent.getHash(),
                downloader.getId()
        );
        TrackedSwarmEntity cachedEntity = cache.get(cacheKey, () -> {
            TrackedSwarmEntity lastData = queryBuilder()
                    .where()
                    .eq("ip", cacheKey.ip)
                    .and()
                    .eq("port", cacheKey.port)
                    .and()
                    .eq("infoHash", cacheKey.infoHash)
                    .and()
                    .eq("downloader", cacheKey.downloader).queryForFirst();
            return Objects.requireNonNullElseGet(lastData, () -> new TrackedSwarmEntity(
                    null,
                    peer.getPeerAddress().getAddress().toNormalizedString(),
                    peer.getPeerAddress().getPort(),
                    torrent.getHash(),
                    torrent.isPrivate(),
                    torrent.getSize(),
                    downloader.getId(),
                    torrent.getProgress(),
                    peer.getPeerId(),
                    peer.getClientName(),
                    peer.getProgress(),
                    0,
                    0,
                    peer.getUploadSpeed(),
                    0,
                    0,
                    peer.getDownloadSpeed(),
                    peer.getFlags() == null ? "" : peer.getFlags().getLtStdString(),
                    new Timestamp(System.currentTimeMillis()),
                    new Timestamp(System.currentTimeMillis())
            ));
        });
        long newDownloaded = peer.getDownloaded() >= cachedEntity.getDownloadedOffset()
                ? peer.getDownloaded() - cachedEntity.getDownloadedOffset()
                : peer.getDownloaded();
        long newUploaded = peer.getUploaded() >= cachedEntity.getUploadedOffset()
                ? peer.getUploaded() - cachedEntity.getUploadedOffset()
                : peer.getUploaded();
        cachedEntity.setDownloaded(cachedEntity.getDownloaded() + newDownloaded);
        cachedEntity.setUploaded(cachedEntity.getUploaded() + newUploaded);
        cachedEntity.setDownloadedOffset(peer.getDownloaded());
        cachedEntity.setUploadedOffset(peer.getUploaded());
        cachedEntity.setClientName(peer.getClientName());
        cachedEntity.setPeerId(peer.getPeerId());
        cachedEntity.setLastFlags(peer.getFlags() == null ? "" : peer.getFlags().getLtStdString());
        cachedEntity.setLastTimeSeen(new Timestamp(System.currentTimeMillis()));
    }

    public void flushAll() {
        try {
            callBatchTasks(() -> {
                for (var value : cache.asMap().values()) {
                    try {
                        createOrUpdate(value);
                    } catch (SQLException e) {
                        log.error("Unable flush back to database for TrackedSwarm: {}", value, e);
                    }
                }
                return null;
            });
        } catch (SQLException e) {
            log.error("Unable to flush all tracked swarm cache to database", e);
        }
    }

    record CacheKey(String ip, int port, String infoHash, String downloader) {
    }
}
