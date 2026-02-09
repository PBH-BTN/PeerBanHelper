package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.peerbanhelper.databasent.mapper.java.AlertMapper;
import com.ghostchu.peerbanhelper.databasent.routing.WriteTransactionTemplate;
import com.ghostchu.peerbanhelper.databasent.service.AlertService;
import com.ghostchu.peerbanhelper.databasent.table.AlertEntity;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AlertServiceImpl extends ServiceImpl<AlertMapper, AlertEntity> implements AlertService {
    @Autowired
    private WriteTransactionTemplate writeTransactionTemplate;

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
    public int deleteOldAlerts(@NotNull OffsetDateTime before) {
        int deleted = 0;
        while (true) {
            // 每次循环在独立事务中执行，完成后释放连接
            Integer changes = writeTransactionTemplate.execute(status -> 
                baseMapper.delete(new LambdaQueryWrapper<AlertEntity>()
                    .le(AlertEntity::getCreateAt, before)
                    .last("LIMIT 150"))
            );
            if (changes == null || changes <= 0) {
                break;
            }
            deleted += changes;
        }
        return deleted;
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
