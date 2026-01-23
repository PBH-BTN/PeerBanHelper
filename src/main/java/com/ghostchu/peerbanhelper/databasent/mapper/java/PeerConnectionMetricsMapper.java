package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.table.PeerConnectionMetricsEntity;

public interface PeerConnectionMetricsMapper extends BaseMapper<PeerConnectionMetricsEntity> {
	void createOrUpdate(PeerConnectionMetricsEntity entityInDb);
}
