package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.peerbanhelper.databasent.dto.PeerBanCount;
import com.ghostchu.peerbanhelper.databasent.mapper.java.HistoryMapper;
import com.ghostchu.peerbanhelper.databasent.service.HistoryService;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import org.springframework.stereotype.Service;

@Service
public class HistoryServiceImpl extends ServiceImpl<HistoryMapper, HistoryEntity> implements HistoryService {

	@Override
	public IPage<PeerBanCount> getBannedIps(Pageable pageable, String filter) {
		Page<PeerBanCount> page = new Page<>(pageable.getPage(), pageable.getSize());
		if (filter != null && !filter.isEmpty()) {
			return baseMapper.getBannedIpsWithFilter(page, filter);
		} else {
			return baseMapper.getBannedIpsWithoutFilter(page);
		}
	}
}
