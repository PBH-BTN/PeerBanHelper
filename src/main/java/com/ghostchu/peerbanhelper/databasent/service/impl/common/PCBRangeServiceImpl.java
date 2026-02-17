package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghostchu.peerbanhelper.databasent.mapper.java.PCBRangeMapper;
import com.ghostchu.peerbanhelper.databasent.service.PCBRangeService;
import com.ghostchu.peerbanhelper.databasent.table.PCBRangeEntity;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
public class PCBRangeServiceImpl extends AbstractCommonService<PCBRangeMapper, PCBRangeEntity> implements PCBRangeService {

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Override
	public List<PCBRangeEntity> fetchFromDatabase(@NotNull String torrentId, @NotNull String downloader) {
        return baseMapper.selectList(new LambdaQueryWrapper<PCBRangeEntity>()
                .eq(PCBRangeEntity::getTorrentId, torrentId)
                .eq(PCBRangeEntity::getDownloader, downloader));
	}

	@Override
	public PCBRangeEntity fetchFromDatabase(@NotNull String torrentId, @NotNull String range, @NotNull String downloader) {
        return baseMapper.selectOne(new LambdaQueryWrapper<PCBRangeEntity>()
                .eq(PCBRangeEntity::getTorrentId, torrentId)
                .eq(PCBRangeEntity::getRange, range)
                .eq(PCBRangeEntity::getDownloader, downloader));
	}

	@Override
	public int deleteEntry(@NotNull String torrentId, @NotNull String range) {
        return baseMapper.delete(new LambdaQueryWrapper<PCBRangeEntity>()
                .eq(PCBRangeEntity::getTorrentId, torrentId)
                .eq(PCBRangeEntity::getRange, range));
	}

	@Override
    public long cleanupDatabase(OffsetDateTime timestamp) {
        return splitBatchDelete(new LambdaQueryWrapper<PCBRangeEntity>()
                .select(PCBRangeEntity::getId)
                .lt(PCBRangeEntity::getLastTimeSeen, timestamp));
	}
}
