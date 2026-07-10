package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.table.PCBRangeEntity;
import org.apache.ibatis.annotations.Param;

public interface PCBRangeMapper extends BaseMapper<PCBRangeEntity> {
	int upsert(@Param("e") PCBRangeEntity entity);
}
