package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;
import org.apache.ibatis.annotations.Param;

public interface TorrentMapper extends BaseMapper<TorrentEntity> {
    int upsert(@Param("e") TorrentEntity entity);
}
