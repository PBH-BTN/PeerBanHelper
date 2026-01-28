package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TorrentServiceImpl extends ServiceImpl<TorrentMapper, TorrentEntity> implements TorrentService {
    private final TransactionTemplate torrentCreateNoTransactionTemplate;

    public TorrentServiceImpl(PlatformTransactionManager transactionManager) {
        torrentCreateNoTransactionTemplate = new TransactionTemplate(transactionManager);
        torrentCreateNoTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    @Override
    public synchronized @NotNull TorrentEntity createIfNotExists(@NotNull TorrentEntity torrent) {
        TorrentEntity existing = queryByInfoHash(torrent.getInfoHash());
        if (existing != null) {
            return existing;
        }
        torrentCreateNoTransactionTemplate.execute(_-> baseMapper.insertOrUpdate(torrent));
        return torrent;
    }

    @Override
    public @Nullable TorrentEntity queryByInfoHash(@NotNull String infoHash) {
        return baseMapper.selectOne(new LambdaQueryWrapper<TorrentEntity>().eq(TorrentEntity::getInfoHash, infoHash));
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
