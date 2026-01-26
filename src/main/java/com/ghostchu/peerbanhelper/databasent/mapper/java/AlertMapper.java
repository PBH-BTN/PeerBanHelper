package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.table.AlertEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;

public interface AlertMapper extends BaseMapper<AlertEntity> {
    @Select("UPDATE alert SET read_at = NOW() WHERE read_at IS NULL")
    int markAllAsRead();

    @Select("UPDATE alert SET read_at = NOW() WHERE identifier = #{identifier} AND read_at IS NULL")
    int markAsRead(@NotNull @Param("identifier") String identifier);
}
