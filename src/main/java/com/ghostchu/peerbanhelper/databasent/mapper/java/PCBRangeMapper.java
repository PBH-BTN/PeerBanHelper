package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.table.PCBRangeEntity;
import org.jetbrains.annotations.NotNull;

public interface PCBRangeMapper extends BaseMapper<PCBRangeEntity> {
    int upsert(@NotNull PCBRangeEntity pcbRangeEntity);
}
