package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.peerbanhelper.databasent.mapper.java.TorrentMapper;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TorrentServiceImpl extends ServiceImpl<TorrentMapper, TorrentEntity> implements TorrentService {

	@Override
	public Optional<TorrentEntity> queryByInfoHash(String infoHash) {
		TorrentEntity torrent = baseMapper.selectOne(new QueryWrapper<TorrentEntity>()
				.eq("info_hash", infoHash)
				.last("limit 1"));
		return Optional.ofNullable(torrent);
	}

	@Override
	public TorrentEntity createIfNotExists(TorrentEntity torrent) {
		baseMapper.createIfNotExists(torrent);
		return torrent;
	}
}
