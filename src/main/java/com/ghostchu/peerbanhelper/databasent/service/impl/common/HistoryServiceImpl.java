package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.peerbanhelper.databasent.dto.PeerBanCount;
import com.ghostchu.peerbanhelper.databasent.dto.UniversalFieldNumResult;
import com.ghostchu.peerbanhelper.databasent.mapper.java.HistoryMapper;
import com.ghostchu.peerbanhelper.databasent.service.HistoryService;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.List;

@Service
public class HistoryServiceImpl extends ServiceImpl<HistoryMapper, HistoryEntity> implements HistoryService {

    @Override
    public IPage<PeerBanCount> getBannedIps(@NotNull Page<PeerBanCount> page, @Nullable String filter) {
        if (filter != null && !filter.isEmpty()) {
            return baseMapper.getBannedIpsWithFilter(page, filter);
        } else {
            return baseMapper.getBannedIpsWithoutFilter(page);
        }
    }

    @Override
    public long countHistoriesByTorrentId(@NotNull Long id) {
        return baseMapper.selectCount(new QueryWrapper<HistoryEntity>().eq("torrent_id", id));
    }

    @Override
    public long countHistoriesByIp(@NotNull InetAddress inetAddress) {
        return baseMapper.selectCount(new QueryWrapper<HistoryEntity>().eq("ip", inetAddress));
    }

    @Override
    public IPage<HistoryEntity> queryBanHistoryByIp(@NotNull Page<HistoryEntity> pageable, @NotNull InetAddress ip,
            @NotNull Orderable orderBy) {
        return baseMapper.selectPage(pageable, orderBy.apply(new QueryWrapper<HistoryEntity>().eq("ip", ip)));
    }

    @Override
    public List<UniversalFieldNumResult> countField(@NotNull String field, double percentFilter,
            @NotNull String downloader, @NotNull Integer substringLength) {
        return baseMapper.countField(field, percentFilter, downloader, substringLength);
    }

    @Override
    public List<UniversalFieldNumResult> sumField(@NotNull String field, double percentFilter,
            @NotNull String downloader, @NotNull Integer substringLength) {
        return baseMapper.sumField(field, percentFilter, downloader, substringLength);
    }
}
