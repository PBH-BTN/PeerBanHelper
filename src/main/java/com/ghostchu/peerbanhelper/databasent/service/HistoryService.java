package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.databasent.dto.PeerBanCount;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.util.query.Pageable;

public interface HistoryService extends IService<HistoryEntity> {
	IPage<PeerBanCount> getBannedIps(Pageable pageable, String filter);
}
