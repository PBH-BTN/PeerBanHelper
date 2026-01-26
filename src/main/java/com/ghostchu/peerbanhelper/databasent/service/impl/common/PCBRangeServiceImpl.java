package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
		return baseMapper.selectList(new QueryWrapper<PCBRangeEntity>()
				.eq("torrent_id", torrentId)
				.eq("downloader", downloader));
	}

	@Override
	public PCBRangeEntity fetchFromDatabase(@NotNull String torrentId, @NotNull String range, @NotNull String downloader) {
		return baseMapper.selectOne(new QueryWrapper<PCBRangeEntity>()
				.eq("torrent_id", torrentId)
				.eq("`range`", range)
				.eq("downloader", downloader));
	}

	@Override
	public int deleteEntry(@NotNull String torrentId, @NotNull String range) {
		return baseMapper.delete(new QueryWrapper<PCBRangeEntity>()
				.eq("torrent_id", torrentId)
				.eq("`range`", range));
	}

	@Override
    public int cleanupDatabase(OffsetDateTime timestamp) {
		return baseMapper.delete(new QueryWrapper<PCBRangeEntity>()
				.lt("last_time_seen", timestamp));
	}
}
