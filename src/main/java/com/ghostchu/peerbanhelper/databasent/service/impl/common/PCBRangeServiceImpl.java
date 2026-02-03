package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.peerbanhelper.databasent.mapper.java.PCBRangeMapper;
import com.ghostchu.peerbanhelper.databasent.service.PCBRangeService;
import com.ghostchu.peerbanhelper.databasent.table.PCBRangeEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class PCBRangeServiceImpl extends ServiceImpl<PCBRangeMapper, PCBRangeEntity> implements PCBRangeService {

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
    public int cleanupDatabase(OffsetDateTime timestamp) {
        int deleted = 0;
        while (true) {
            List<PCBRangeEntity> list = baseMapper.selectList(new LambdaQueryWrapper<PCBRangeEntity>()
                    .select(PCBRangeEntity::getId)
                    .lt(PCBRangeEntity::getLastTimeSeen, timestamp)
                    .last("LIMIT 1000"));
            if (list.isEmpty()) {
                break;
            }
            deleted += baseMapper.deleteByIds(list);
        }
        return deleted;
	}
}
