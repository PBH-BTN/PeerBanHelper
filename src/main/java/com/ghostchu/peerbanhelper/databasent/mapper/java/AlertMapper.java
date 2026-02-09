package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.table.AlertEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

public interface AlertMapper extends BaseMapper<AlertEntity> {
    @Update("UPDATE alert SET read_at = #{readAt} WHERE read_at IS NULL")
    void markAllAsRead(@NotNull OffsetDateTime readAt);

    @Update("UPDATE alert SET read_at = #{readAt} WHERE identifier = #{identifier} AND read_at IS NULL")
    void markAsRead(@NotNull @Param("identifier") String identifier, @NotNull OffsetDateTime readAt);
}
