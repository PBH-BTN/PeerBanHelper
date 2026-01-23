package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.table.AlertEntity;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;

public interface AlertMapper extends BaseMapper<AlertEntity> {
    int markAllAsRead();

    int markAsRead(@NotNull @Param("identifier") String identifier);
}
