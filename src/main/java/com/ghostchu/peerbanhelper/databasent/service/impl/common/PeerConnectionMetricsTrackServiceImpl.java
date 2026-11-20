package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.databasent.mapper.java.PeerConnectionMetricsTrackMapper;
import com.ghostchu.peerbanhelper.databasent.service.PeerBatchLookup.Endpoint;
import com.ghostchu.peerbanhelper.databasent.service.PeerBatchLookup;
import com.ghostchu.peerbanhelper.databasent.service.PeerConnectionMetricsTrackService;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.databasent.table.PeerConnectionMetricsTrackEntity;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.util.Pair;
import com.ghostchu.peerbanhelper.util.TimeUtil;
import com.ghostchu.peerbanhelper.util.iocache.PBHCache;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.HashMap;
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
        batchFlushDatabase(cache.asMap().entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue())));
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
    public void syncPeers(@NotNull Downloader downloader, @NotNull Torrent torrent, @NotNull List<? extends Peer> peers) throws ExecutionException {
        TorrentEntity torrentEntity = torrentService.createIfNotExists(new TorrentEntity(
                null,
                torrent.getHash(),
                torrent.getName(),
                torrent.getSize(),
                torrent.isPrivate()
        ));
        var day = TimeUtil.getStartOfToday(System.currentTimeMillis());
        for (var batch : Lists.partition(peers, PeerBatchLookup.BATCH_SIZE)) {
            syncBatch(downloader.getId(), torrentEntity.getId(), day, batch);
        }
    }

    private void syncBatch(String downloader, long torrentId, OffsetDateTime day, List<? extends Peer> peers) throws ExecutionException {
        var lookup = new PeerBatchLookup<>(cache,
                () -> peers.stream().filter(peer -> !peer.isHandshaking()).map(peer -> new CacheKey(day, downloader, torrentId, Endpoint.of(peer))).toList(),
                keys -> {
                    var query = scope(day, downloader, torrentId);
                    var found = new HashMap<CacheKey, PeerConnectionMetricsTrackEntity>();
                    for (var row : baseMapper.selectList(PeerBatchLookup.matching(query, keys.stream().map(CacheKey::endpoint).toList(),
                            PeerConnectionMetricsTrackEntity::getAddress, PeerConnectionMetricsTrackEntity::getPort))) {
                        found.put(new CacheKey(day, downloader, torrentId, new Endpoint(row.getAddress(), row.getPort())), row);
                    }
                    return found;
                });
        for (Peer peer : peers) {
            if (peer.isHandshaking()) continue;
            var cacheKey = new CacheKey(day, downloader, torrentId, Endpoint.of(peer));
            PeerConnectionMetricsTrackEntity trackEntity = cache.getPendingOrLoad(cacheKey, () -> {
                var entity = lookup.load(cacheKey, () -> baseMapper.selectOne(scope(day, downloader, torrentId)
                        .eq(PeerConnectionMetricsTrackEntity::getAddress, cacheKey.endpoint.address())
                        .eq(PeerConnectionMetricsTrackEntity::getPort, cacheKey.endpoint.port())));
                if (entity == null) {
                    entity = new PeerConnectionMetricsTrackEntity();
                    entity.setTimeframeAt(cacheKey.timeframeAt());
                }
                return entity;
            });
            trackEntity.setPeerId(peer.getPeerId());
            trackEntity.setClientName(peer.getClientName());
            trackEntity.setLastFlags(peer.getFlags() == null ? null : peer.getFlags().getLtStdString());
            trackEntity.setDownloader(downloader);
            trackEntity.setTorrentId(torrentId);
            trackEntity.setAddress(peer.getPeerAddress().getAddress().toInetAddress());
            trackEntity.setPort(peer.getPeerAddress().getPort());
            //baseMapper.insertOrUpdate(trackEntity);
        }
    }

    private LambdaQueryWrapper<PeerConnectionMetricsTrackEntity> scope(OffsetDateTime day, String downloader, long torrentId) {
        return new LambdaQueryWrapper<PeerConnectionMetricsTrackEntity>().eq(PeerConnectionMetricsTrackEntity::getTimeframeAt, day)
                .eq(PeerConnectionMetricsTrackEntity::getDownloader, downloader).eq(PeerConnectionMetricsTrackEntity::getTorrentId, torrentId);
    }

    public record CacheKey(OffsetDateTime timeframeAt, String downloader, long torrentId, Endpoint endpoint) {
    }
}
