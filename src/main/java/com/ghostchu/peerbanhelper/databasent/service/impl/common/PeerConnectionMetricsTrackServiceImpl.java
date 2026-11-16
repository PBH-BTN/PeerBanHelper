package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.databasent.mapper.java.PeerConnectionMetricsTrackMapper;
import com.ghostchu.peerbanhelper.databasent.service.PeerConnectionMetricsTrackService;
import com.ghostchu.peerbanhelper.databasent.service.PeerConnectionMetricsService;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.databasent.table.PeerConnectionMetricsTrackEntity;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.util.TimeUtil;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class PeerConnectionMetricsTrackServiceImpl extends AbstractCommonService<PeerConnectionMetricsTrackMapper, PeerConnectionMetricsTrackEntity> implements PeerConnectionMetricsTrackService {

    private static final int PAGE_SIZE = 500;
    private final int bufferSize = Math.max(1, ExternalSwitch.parseInt("pbh.module.session-analyse-service-module.cache-size", 1000));
    private final LinkedHashMap<CacheKey, PeerConnectionMetricsTrackEntity> pending = new LinkedHashMap<>();

    @Autowired
    private TorrentService torrentService;
    @Autowired
    private PeerConnectionMetricsService metricsService;

    public PeerConnectionMetricsTrackServiceImpl(@NotNull TransactionTemplate transactionTemplate) {
        super(transactionTemplate);
    }

    @Override
    public synchronized void aggregatePending() {
        transactionTemplate.executeWithoutResult(status -> {
            if (!baseMapper.exists(new LambdaQueryWrapper<>())) {
                saveMetrics(List.copyOf(pending.values()));
                return;
            }

            // Spill and merge on the same unique key before counting any sessions.
            pending.values().forEach(baseMapper::upsert);
            while (true) {
                var batch = baseMapper.selectList(new LambdaQueryWrapper<PeerConnectionMetricsTrackEntity>()
                        .orderByAsc(PeerConnectionMetricsTrackEntity::getId)
                        .last("LIMIT " + PAGE_SIZE));
                if (batch.isEmpty()) break;
                saveMetrics(batch);
                baseMapper.deleteByIds(batch);
            }
        });
        pending.clear();
    }

    private void saveMetrics(List<PeerConnectionMetricsTrackEntity> sessions) {
        metricsService.saveAggregating(metricsService.aggregating(sessions), false);
    }

    private void spillPending() {
        transactionTemplate.executeWithoutResult(status -> pending.values().forEach(baseMapper::upsert));
        pending.clear();
    }

    @Override
    public synchronized void syncPeers(@NotNull Downloader downloader, @NotNull Torrent torrent, @NotNull List<? extends Peer> peers) {
        TorrentEntity torrentEntity = torrentService.createIfNotExists(new TorrentEntity(
                null,
                torrent.getHash(),
                torrent.getName(),
                torrent.getSize(),
                torrent.isPrivate()
        ));
        var timeframe = TimeUtil.getStartOfToday(System.currentTimeMillis());
        for (Peer peer : peers) {
            if (peer.isHandshaking()) continue;
            CacheKey cacheKey = new CacheKey(
                    timeframe,
                    downloader.getId(),
                    torrentEntity.getId(),
                    peer.getPeerAddress().getAddress().toInetAddress(),
                    peer.getPeerAddress().getPort()
            );
            if (pending.size() >= bufferSize && !pending.containsKey(cacheKey)) {
                spillPending();
            }
            var trackEntity = pending.computeIfAbsent(cacheKey, key ->
                    new PeerConnectionMetricsTrackEntity(null, key.timeframeAt(), key.downloader(),
                            key.torrentId(), key.address(), key.port(), null, null, null));
            trackEntity.setPeerId(peer.getPeerId());
            trackEntity.setClientName(peer.getClientName());
            trackEntity.setLastFlags(peer.getFlags() == null ? null : peer.getFlags().getLtStdString());
        }
    }

    public record CacheKey(OffsetDateTime timeframeAt, String downloader, long torrentId, InetAddress address, int port) {
    }
}
