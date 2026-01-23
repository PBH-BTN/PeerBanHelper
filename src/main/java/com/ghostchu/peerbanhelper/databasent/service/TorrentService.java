package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;

import java.util.Optional;

public interface TorrentService extends IService<TorrentEntity> {
	Optional<TorrentEntity> queryByInfoHash(String infoHash);

	TorrentEntity createIfNotExists(TorrentEntity torrent);
}
