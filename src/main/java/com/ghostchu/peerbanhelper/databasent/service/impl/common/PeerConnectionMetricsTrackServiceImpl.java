package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.databasent.mapper.java.PeerConnectionMetricsTrackMapper;
import com.ghostchu.peerbanhelper.databasent.service.PeerConnectionMetricsTrackService;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.databasent.table.PeerConnectionMetricsTrackEntity;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class PeerConnectionMetricsTrackServiceImpl extends ServiceImpl<PeerConnectionMetricsTrackMapper, PeerConnectionMetricsTrackEntity> implements PeerConnectionMetricsTrackService {
    private final Cache<@NotNull CacheKey, @NotNull PeerConnectionMetricsTrackEntity> cache = CacheBuilder.newBuilder()
            .maximumSize(ExternalSwitch.parseInt("pbh.module.session-analyse-service-module.cache-size", 1000))
            .expireAfterAccess(3, TimeUnit.MINUTES)
            .removalListener((RemovalListener<@NotNull CacheKey, @NotNull PeerConnectionMetricsTrackEntity>) notification -> {
                var v = notification.getValue();
                //noinspection ConstantValue
                if (v != null) {
                    baseMapper.insertOrUpdate(v);
                }
            })
            .softValues()
            .build();

    @Autowired
    private TorrentService torrentService;

    @Override
    public void flushAll() {
        baseMapper.insertOrUpdate(cache.asMap().values());
    }

    @Override
    public int deleteEntries(@NotNull List<PeerConnectionMetricsTrackEntity> entities) {
        return baseMapper.deleteByIds(entities);
    }

    @Override
    public void syncPeers(@NotNull Downloader downloader, @NotNull Torrent torrent, @NotNull List<Peer> peers) throws SQLException, ExecutionException {
        TorrentEntity torrentEntity = torrentService.createIfNotExists(new TorrentEntity(
                null,
                torrent.getHash(),
                torrent.getName(),
                torrent.getSize(),
                torrent.isPrivate()
        ));
        for (Peer peer : peers) {
            if (peer.isHandshaking()) continue;
            CacheKey cacheKey = new CacheKey(
                    MiscUtil.getStartOfToday(System.currentTimeMillis()),
                    downloader.getId(),
                    torrentEntity.getId(),
                    peer.getPeerAddress().getAddress().toNormalizedString(),
                    peer.getPeerAddress().getPort()
            );
            PeerConnectionMetricsTrackEntity trackEntity = cache.get(cacheKey, () -> {
                PeerConnectionMetricsTrackEntity entity = baseMapper.selectOne(new QueryWrapper<PeerConnectionMetricsTrackEntity>()
                        .eq("timeframe_at", cacheKey.timeframeAt())
                        .eq("downloader", cacheKey.downloader())
                        .eq("torrent_id", cacheKey.torrentId())
                        .eq("address", cacheKey.address())
                        .eq("port", cacheKey.port())
                );
                if (entity == null) {
                    entity = new PeerConnectionMetricsTrackEntity();
                    entity.setTimeframeAt(cacheKey.timeframeAt());
                    entity.setDownloader(downloader.getId());
                    entity.setTorrentId(torrentEntity.getId());
                    entity.setAddress(peer.getPeerAddress().getAddress().toInetAddress());
                    entity.setPort(peer.getPeerAddress().getPort());
                }
                return entity;
            });
            trackEntity.setPeerId(peer.getPeerId());
            trackEntity.setClientName(peer.getClientName());
            trackEntity.setLastFlags(peer.getFlags() == null ? null : peer.getFlags().getLtStdString());
        }
    }

    public record CacheKey(OffsetDateTime timeframeAt, String downloader, long torrentId, String address, int port) {
    }
}
