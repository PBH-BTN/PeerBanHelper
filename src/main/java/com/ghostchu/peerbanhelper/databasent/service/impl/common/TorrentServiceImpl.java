package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.peerbanhelper.databasent.mapper.java.TorrentMapper;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TorrentServiceImpl extends AbstractCommonService<TorrentMapper, TorrentEntity> implements TorrentService {
    private final Cache<@NotNull String, @NotNull TorrentEntity> instanceCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(1000)
            .softValues()
            .build();

    @Override
    public @NotNull TorrentEntity createIfNotExists(@NotNull TorrentEntity torrent) {
        TorrentEntity existing = queryByInfoHash(torrent.getInfoHash());
        if (existing != null) {
            // If existing record is complete, or invalidation won't help (incoming data has no value), return existing
            // This restores the cache hit optimization for the majority of calls (torrent exists and is valid)
            boolean existingIsComplete = (existing.getSize() != null && existing.getSize() > 0) && existing.getPrivateTorrent() != null;
            boolean incomingIsPoor = (torrent.getSize() == null || torrent.getSize() <= 0) && torrent.getPrivateTorrent() == null;
            if (existingIsComplete || incomingIsPoor) {
                return existing;
            }
        }

        baseMapper.upsert(torrent);
        instanceCache.invalidate(torrent.getInfoHash());
        TorrentEntity torrentEntity = queryByInfoHash(torrent.getInfoHash());
        if (torrentEntity == null) {
            throw new IllegalStateException("Failed to retrieve torrent after upsert: " + torrent.getInfoHash());
        }
        return torrentEntity;
    }

    @Override
    public @Nullable TorrentEntity queryByInfoHash(@NotNull String infoHash) {
        var cached = instanceCache.getIfPresent(infoHash);
        if (cached != null && cached.getId() != null) {
            return cached;
        }
        var entity = baseMapper.selectOne(new LambdaQueryWrapper<TorrentEntity>().eq(TorrentEntity::getInfoHash, infoHash)
                .last("limit 1"));
        if (entity != null) {
            instanceCache.put(infoHash, entity);
        }
        return entity;
    }

    @Override
    public IPage<TorrentEntity> search(Page<TorrentEntity> page, String keyword, Orderable normalSort, String statsSortField, boolean statsSortAsc) {
        // Here we keep QueryWrapper for complex dynamic sorting with raw SQL (last clause) and OR condition which is concise.
        // Although lambda can do OR, `last` with raw SQL for subquery sorting is specific.
        // Also `Orderable` is used in other methods (not here explicitly but parameter `normalSort` is present).
        // Wait, `normalSort` is passed but not used?
        // Let's check existing code lines 40+
        QueryWrapper<TorrentEntity> queryWrapper = new QueryWrapper<>();
        if (keyword != null) {
            queryWrapper.and(q -> q.like("name", keyword)
                    .or()
                    .like("info_hash", keyword));
        }

        if (statsSortField != null) {
            String subQueryTable = "peerBanCount".equals(statsSortField) ? "history" : "peer_records";
            String sortDirection = statsSortAsc ? "ASC" : "DESC";
            queryWrapper.last("ORDER BY (SELECT COUNT(*) FROM " + subQueryTable +
                    " WHERE " + subQueryTable + ".torrent_id = torrents.id) " + sortDirection + ", id DESC");
        } else {
            if (normalSort != null) {
                normalSort.apply(queryWrapper);
            }
        }
        return baseMapper.selectPage(page, queryWrapper);
    }
}
