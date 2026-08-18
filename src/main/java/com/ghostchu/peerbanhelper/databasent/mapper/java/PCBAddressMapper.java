package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.table.PCBAddressEntity;
import org.jetbrains.annotations.NotNull;

public interface PCBAddressMapper extends BaseMapper<PCBAddressEntity> {
    int upsert(@NotNull PCBAddressEntity pcbAddressEntity);
}
