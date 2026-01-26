package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.peerbanhelper.databasent.mapper.java.TorrentMapper;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class TorrentServiceImpl extends ServiceImpl<TorrentMapper, TorrentEntity> implements TorrentService {
    @Override
    public @NotNull TorrentEntity createIfNotExists(@NotNull TorrentEntity torrent) {
        baseMapper.insertOrUpdate(torrent);
        return torrent;
    }

    @Override
    public @Nullable TorrentEntity queryByInfoHash(@NotNull String infoHash) {
        return baseMapper.selectOne(new QueryWrapper<TorrentEntity>().eq("info_hash", infoHash));
    }

    @Override
    public IPage<TorrentEntity> search(Page<TorrentEntity> page, String keyword, Orderable normalSort, String statsSortField, boolean statsSortAsc) {
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
