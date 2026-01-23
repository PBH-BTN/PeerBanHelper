package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.peerbanhelper.databasent.mapper.java.AlertMapper;
import com.ghostchu.peerbanhelper.databasent.service.AlertService;
import com.ghostchu.peerbanhelper.databasent.table.AlertEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AlertServiceImpl extends ServiceImpl<AlertMapper, AlertEntity> implements AlertService {
    @Override
    public @NotNull List<AlertEntity> getUnreadAlerts() {
        return baseMapper.selectList(new QueryWrapper<AlertEntity>().isNull("read_at").orderByDesc("created_at"));
    }

    @Override
    public boolean identifierAlertExists(@NotNull String identifier) {
        return baseMapper.exists(new QueryWrapper<AlertEntity>().eq("identifier", identifier).isNull("read_at"));
    }

    @Override
    public boolean identifierAlertExistsIncludeRead(@NotNull String identifier) {
        return baseMapper.exists(new QueryWrapper<AlertEntity>().eq("identifier", identifier));
    }

    @Override
    public int deleteOldAlerts(@NotNull OffsetDateTime before) {
        return baseMapper.delete(new QueryWrapper<AlertEntity>().lt("created_at", before));
    }

    @Override
    public int markAllAsRead() {
        return baseMapper.markAllAsRead();
    }

    @Override
    public int markAsRead(@NotNull String identifier) {
        return baseMapper.markAsRead(identifier);
    }
}
