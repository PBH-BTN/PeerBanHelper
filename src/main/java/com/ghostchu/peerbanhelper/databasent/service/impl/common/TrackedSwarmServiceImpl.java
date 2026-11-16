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
import com.ghostchu.peerbanhelper.util.query.Pageable;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;

@Component
@Slf4j
public class TrackedSwarmServiceImpl extends AbstractCommonService<TrackedSwarmMapper, TrackedSwarmEntity> implements TrackedSwarmService {
    private static final int CLEANUP_BATCH_SIZE = 500;
    private final TransactionTemplate transactionTemplate;
    private final int cacheSize = Math.max(1, ExternalSwitch.parseInt("pbh.module.swarm-tracking-module.cache-size", 1000));
    private final LinkedHashMap<CacheKey, TrackedSwarmEntity> cache = new LinkedHashMap<>(16, 0.75f, true);

    public TrackedSwarmServiceImpl(@NotNull TransactionTemplate transactionTemplate) {
        super(transactionTemplate);
        this.transactionTemplate = transactionTemplate;
    }


    @Override
    public @NotNull Page<TrackedSwarmEntity> getPendingSubmitTrackedPeers(@NotNull Pageable pageable, long idAfterThan) {
        return baseMapper.selectPage(pageable.toPage(), new LambdaQueryWrapper<TrackedSwarmEntity>().gt(TrackedSwarmEntity::getId, idAfterThan).orderByAsc(TrackedSwarmEntity::getId));
    }

    @Override
    public synchronized void syncPeers(@NotNull Downloader downloader, @NotNull Torrent torrent, @NotNull Peer peer) {
        CacheKey cacheKey = new CacheKey(
                peer.getPeerAddress().getAddress().toCompressedString(),
                peer.getPeerAddress().getPort(),
                torrent.getHash(),
                downloader.getId()
        );
        TrackedSwarmEntity cachedEntity = cache.get(cacheKey);
        if (cachedEntity == null) {
            if (cache.size() >= cacheSize) {
                flushAll();
                for (int count = Math.max(1, cacheSize / 10); count > 0; count--) {
                    cache.pollFirstEntry();
                }
            }

            TrackedSwarmEntity lastData = baseMapper.selectOne(new LambdaQueryWrapper<TrackedSwarmEntity>()
                    .eq(TrackedSwarmEntity::getIp, peer.getPeerAddress().getAddress().toInetAddress())
                    .eq(TrackedSwarmEntity::getPort, cacheKey.port)
                    .eq(TrackedSwarmEntity::getInfoHash, cacheKey.infoHash)
                    .eq(TrackedSwarmEntity::getDownloader, cacheKey.downloader)
                    .orderByDesc(TrackedSwarmEntity::getId)
                    .last("LIMIT 1")
            );

            if (lastData == null) {
                lastData = new TrackedSwarmEntity(
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
                lastData.setDirty(true);
            }
            cachedEntity = lastData;
            cache.put(cacheKey, cachedEntity);
        }
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
    public synchronized void flushAll() {
        var dirty = cache.values().stream().filter(TrackedSwarmEntity::isDirty).toList();
        if (dirty.isEmpty()) return;
        transactionTemplate.executeWithoutResult(status -> dirty.forEach(baseMapper::upsert));
        dirty.forEach(entity -> entity.setDirty(false));
    }

    @Override
    public void removeInactive(OffsetDateTime cutoff) {
        while (removeInactiveBatch(cutoff) == CLEANUP_BATCH_SIZE) {
            // Release the lock and connection between batches.
        }
    }

    private synchronized int removeInactiveBatch(OffsetDateTime cutoff) {
        cache.values().removeIf(entity -> entity.getLastTimeSeen().isBefore(cutoff));
        flushAll();
        var expired = new LambdaQueryWrapper<TrackedSwarmEntity>()
                .lt(TrackedSwarmEntity::getLastTimeSeen, cutoff);
        var ids = baseMapper.selectList(expired.clone()
                        .select(TrackedSwarmEntity::getId)
                        .orderByAsc(TrackedSwarmEntity::getLastTimeSeen)
                        .last("LIMIT " + CLEANUP_BATCH_SIZE))
                .stream().map(TrackedSwarmEntity::getId).toList();
        if (ids.isEmpty()) return 0;
        baseMapper.delete(expired.in(TrackedSwarmEntity::getId, ids));
        return ids.size();
    }

    @Override
    public synchronized void closeCache() {
        flushAll();
        cache.clear();
    }

    @Override
    public synchronized void resetTable() {
        baseMapper.resetTable();
        cache.clear();
    }

    record CacheKey(String ip, int port, String infoHash, String downloader) {
    }
}
