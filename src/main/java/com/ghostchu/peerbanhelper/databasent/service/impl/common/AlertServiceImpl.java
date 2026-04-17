package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghostchu.peerbanhelper.databasent.mapper.java.AlertMapper;
import com.ghostchu.peerbanhelper.databasent.service.AlertService;
import com.ghostchu.peerbanhelper.databasent.table.AlertEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AlertServiceImpl extends AbstractCommonService<AlertMapper, AlertEntity> implements AlertService {
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Override
    public @NotNull List<AlertEntity> getUnreadAlerts() {
        return baseMapper.selectList(new LambdaQueryWrapper<AlertEntity>().isNull(AlertEntity::getReadAt).orderByDesc(AlertEntity::getCreateAt));
    }

    @Override
    public boolean identifierAlertExists(@NotNull String identifier) {
        return baseMapper.exists(new LambdaQueryWrapper<AlertEntity>().eq(AlertEntity::getIdentifier, identifier).isNull(AlertEntity::getReadAt));
    }

    @Override
    public boolean identifierAlertExistsIncludeRead(@NotNull String identifier) {
        return baseMapper.exists(new LambdaQueryWrapper<AlertEntity>().eq(AlertEntity::getIdentifier, identifier));
    }

    @Override
    public long deleteOldAlerts(@NotNull OffsetDateTime before) {
        return splitBatchDelete(new LambdaQueryWrapper<AlertEntity>().select(AlertEntity::getId).le(AlertEntity::getCreateAt, before));
    }

    @Override
    public void markAllAsRead() {
        baseMapper.markAllAsRead(OffsetDateTime.now());
    }

    @Override
    public void markAsRead(@NotNull String identifier) {
        baseMapper.markAsRead(identifier, OffsetDateTime.now());
    }
}
