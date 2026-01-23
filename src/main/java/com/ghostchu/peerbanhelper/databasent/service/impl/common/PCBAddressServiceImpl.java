package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.peerbanhelper.databasent.mapper.java.PCBAddressMapper;
import com.ghostchu.peerbanhelper.databasent.service.PCBAddressService;
import com.ghostchu.peerbanhelper.databasent.table.PCBAddressEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
public class PCBAddressServiceImpl extends ServiceImpl<PCBAddressMapper, PCBAddressEntity> implements PCBAddressService {

	@Override
	public List<PCBAddressEntity> fetchFromDatabase(@NotNull String torrentId, @NotNull String downloader) {
		return baseMapper.selectList(new QueryWrapper<PCBAddressEntity>()
				.eq("torrent_id", torrentId)
				.eq("downloader", downloader));
	}

	@Override
	public PCBAddressEntity fetchFromDatabase(@NotNull String torrentId, @NotNull String ip, int port, @NotNull String downloader) {
		return baseMapper.selectOne(new QueryWrapper<PCBAddressEntity>()
				.eq("torrent_id", torrentId)
				.eq("ip", ip)
				.eq("port", port)
				.eq("downloader", downloader));
	}

	@Override
	public int deleteEntry(@NotNull String torrentId, @NotNull String ip) {
		return baseMapper.delete(new QueryWrapper<PCBAddressEntity>()
				.eq("torrent_id", torrentId)
				.eq("ip", ip));
	}

	@Override
	public int cleanupDatabase(Timestamp timestamp) {
		return baseMapper.delete(new QueryWrapper<PCBAddressEntity>()
				.lt("last_time_seen", timestamp));
	}
}
