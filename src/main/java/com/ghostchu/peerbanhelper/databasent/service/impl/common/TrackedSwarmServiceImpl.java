package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.databasent.mapper.java.TrackedSwarmMapper;
import com.ghostchu.peerbanhelper.databasent.service.TrackedSwarmService;
import com.ghostchu.peerbanhelper.databasent.table.tmp.TrackedSwarmEntity;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.util.Pair;
import com.ghostchu.peerbanhelper.util.iocache.PBHCache;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

@Component
@Slf4j
public class TrackedSwarmServiceImpl extends AbstractCommonService<TrackedSwarmMapper, TrackedSwarmEntity> implements TrackedSwarmService {
    @Autowired
    private TransactionTemplate transactionTemplate;
    private final PBHCache<@NotNull CacheKey, @NotNull TrackedSwarmEntity> cache = new PBHCache<>(
            ExternalSwitch.parseInt("pbh.module.swarm-tracking-module.cache-size", 1000),
            null,
            3 * 60 * 1000L,
            false,
            false,
            true,
            this::batchFlushDatabase
    );

    public TrackedSwarmServiceImpl(@NotNull TransactionTemplate transactionTemplate) {
        super(transactionTemplate);
    }


    @Override
    public @NotNull Page<TrackedSwarmEntity> getPendingSubmitTrackedPeers(@NotNull Pageable pageable, long idAfterThan) {
        return baseMapper.selectPage(pageable.toPage(), new LambdaQueryWrapper<TrackedSwarmEntity>().gt(TrackedSwarmEntity::getId, idAfterThan).orderByAsc(TrackedSwarmEntity::getId));
    }

    @Override
    public void syncPeers(@NotNull Downloader downloader, @NotNull Torrent torrent, @NotNull Peer peer) throws ExecutionException {
        CacheKey cacheKey = new CacheKey(
                peer.getPeerAddress().getAddress().toCompressedString(),
                peer.getPeerAddress().getPort(),
                torrent.getHash(),
                downloader.getId()
        );
        TrackedSwarmEntity cachedEntity = cache.get(cacheKey, () -> {

            TrackedSwarmEntity lastData = baseMapper.selectOne(new LambdaQueryWrapper<TrackedSwarmEntity>()
                    .eq(TrackedSwarmEntity::getIp, InetAddress.getByName(cacheKey.ip))
                    .eq(TrackedSwarmEntity::getPort, cacheKey.port)
                    .eq(TrackedSwarmEntity::getInfoHash, cacheKey.infoHash)
                    .eq(TrackedSwarmEntity::getDownloader, cacheKey.downloader)
                    .orderByDesc(TrackedSwarmEntity::getId)
                    .last("LIMIT 1")
            );

            if (lastData != null) {
                lastData.setLastTimeSeen(OffsetDateTime.now());
                return lastData;
            }

            return new TrackedSwarmEntity(
                    null,
                    peer.getPeerAddress().getAddress().toInetAddress(),
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
                    OffsetDateTime.now(),
                    OffsetDateTime.now(),
                    peer.getDownloadSpeed(),
                    peer.getUploadSpeed()
            );
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
        cachedEntity.setLastTimeSeen(OffsetDateTime.now());
        cachedEntity.setDownloadSpeedMax(Math.max(peer.getDownloadSpeed(), cachedEntity.getDownloadSpeedMax()));
        cachedEntity.setUploadSpeedMax(Math.max(peer.getUploadSpeed(), cachedEntity.getUploadSpeedMax()));
    }

    @Override
    public void flushAll() {
        transactionTemplate.execute(_ -> {
            for (TrackedSwarmEntity entity : cache.asMap().values()) {
                baseMapper.upsert(entity);
            }
            return null;
        });
    }

    @Override
    public void closeCache() throws Exception {
        cache.close();
    }

    private void batchFlushDatabase(Stream<Pair<CacheKey, TrackedSwarmEntity>> stream) {
        transactionTemplate.execute(_ -> {
            stream.map(Pair::getRight).forEach(baseMapper::upsert);
            return null;
        });
    }

    @Override
    public void resetTable() {
        baseMapper.resetTable();
    }

    record CacheKey(String ip, int port, String infoHash, String downloader) {
    }
}
