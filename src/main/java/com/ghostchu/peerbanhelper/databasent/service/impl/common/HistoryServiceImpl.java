package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.peerbanhelper.databasent.dto.PeerBanCount;
import com.ghostchu.peerbanhelper.databasent.mapper.java.HistoryMapper;
import com.ghostchu.peerbanhelper.databasent.service.HistoryService;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.net.InetAddress;

@Service
public class HistoryServiceImpl extends ServiceImpl<HistoryMapper, HistoryEntity> implements HistoryService {

	@Override
    public IPage<PeerBanCount> getBannedIps(@NotNull Pageable pageable, @Nullable String filter) {
		Page<PeerBanCount> page = new Page<>(pageable.getPage(), pageable.getSize());
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
}
