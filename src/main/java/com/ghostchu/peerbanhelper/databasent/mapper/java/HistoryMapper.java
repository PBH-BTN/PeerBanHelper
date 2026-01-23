package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.dto.PeerBanCount;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;

public interface HistoryMapper extends BaseMapper<HistoryEntity> {
	IPage<PeerBanCount> getBannedIpsWithFilter(IPage<PeerBanCount> page, @Param("filter") String filter);

	IPage<PeerBanCount> getBannedIpsWithoutFilter(IPage<PeerBanCount> page);

	long countDistinctIpWithFilter(@Param("filter") String filter);

	long countDistinctIp();
}
