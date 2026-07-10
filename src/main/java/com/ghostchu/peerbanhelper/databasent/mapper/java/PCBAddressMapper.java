package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.table.PCBAddressEntity;
import org.apache.ibatis.annotations.Param;

public interface PCBAddressMapper extends BaseMapper<PCBAddressEntity> {
	int upsert(@Param("e") PCBAddressEntity entity);
}
