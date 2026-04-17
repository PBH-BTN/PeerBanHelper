package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.databasent.mapper.java.PeerConnectionMetricsTrackMapper;
import com.ghostchu.peerbanhelper.databasent.service.PeerConnectionMetricsTrackService;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.databasent.table.PeerConnectionMetricsTrackEntity;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.util.Pair;
import com.ghostchu.peerbanhelper.util.TimeUtil;
import com.ghostchu.peerbanhelper.util.iocache.PBHCache;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

@Service
public class PeerConnectionMetricsTrackServiceImpl extends AbstractCommonService<PeerConnectionMetricsTrackMapper, PeerConnectionMetricsTrackEntity> implements PeerConnectionMetricsTrackService {

    private final PBHCache<@NotNull CacheKey, @NotNull PeerConnectionMetricsTrackEntity> cache = new PBHCache<>(
            ExternalSwitch.parseInt("pbh.module.session-analyse-service-module.cache-size", 1000),
            null,
            3 * 60 * 1000L,
            false,
            false,
            true,
            this::batchFlushDatabase
    );

    @Autowired
    private TorrentService torrentService;

    public PeerConnectionMetricsTrackServiceImpl(@NotNull TransactionTemplate transactionTemplate) {
        super(transactionTemplate);
    }

    @Override
    public void flushAll() {
        transactionTemplate.execute(_->{
            for (PeerConnectionMetricsTrackEntity value : cache.asMap().values()) {
                baseMapper.upsert(value);
            }
            return null;
        });
    }

    @Override
    public void closeCache() throws Exception {
        cache.close();
    }

    private void batchFlushDatabase(Stream<Pair<CacheKey, PeerConnectionMetricsTrackEntity>> stream) {
        transactionTemplate.execute(_ -> {
            stream.map(Pair::getRight).forEach(baseMapper::upsert);
            return null;
        });
    }

    @Override
    public int deleteEntries(@NotNull List<PeerConnectionMetricsTrackEntity> entities) {
        return baseMapper.deleteByIds(entities);
    }

    @Override
    public void syncPeers(@NotNull Downloader downloader, @NotNull Torrent torrent, @NotNull List<Peer> peers) throws ExecutionException {
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
                    TimeUtil.getStartOfToday(System.currentTimeMillis()),
                    downloader.getId(),
                    torrentEntity.getId(),
                    peer.getPeerAddress().getAddress().toCompressedString(),
                    peer.getPeerAddress().getPort()
            );
            PeerConnectionMetricsTrackEntity trackEntity = cache.get(cacheKey, () -> {
                PeerConnectionMetricsTrackEntity entity = baseMapper.selectOne(new LambdaQueryWrapper<PeerConnectionMetricsTrackEntity>()
                        .eq(PeerConnectionMetricsTrackEntity::getTimeframeAt, cacheKey.timeframeAt())
                        .eq(PeerConnectionMetricsTrackEntity::getDownloader, cacheKey.downloader())
                        .eq(PeerConnectionMetricsTrackEntity::getTorrentId, cacheKey.torrentId())
                        .eq(PeerConnectionMetricsTrackEntity::getAddress, InetAddress.getByName(cacheKey.address()))
                        .eq(PeerConnectionMetricsTrackEntity::getPort, cacheKey.port())
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
