package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

public interface TorrentMapper extends BaseMapper<TorrentEntity> {

	Optional<TorrentEntity> queryByInfoHash(@Param("infoHash") String infoHash);

	TorrentEntity createIfNotExists(@Param("torrent") TorrentEntity torrent);
}
