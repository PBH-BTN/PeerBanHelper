package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.peerbanhelper.databasent.mapper.java.AlertMapper;
import com.ghostchu.peerbanhelper.databasent.service.AlertService;
import com.ghostchu.peerbanhelper.databasent.table.AlertEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AlertServiceImpl extends ServiceImpl<AlertMapper, AlertEntity> implements AlertService {
    @Override
    public @NotNull List<AlertEntity> getUnreadAlertsUnPaged() throws SQLException {
        return List.of();
    }

    @Override
    public boolean identifierAlertExists(@NotNull String identifier) throws SQLException {
        return false;
    }

    @Override
    public boolean identifierAlertExistsIncludeRead(@NotNull String identifier) throws SQLException {
        return false;
    }

    @Override
    public int deleteOldAlerts(@NotNull OffsetDateTime before) throws SQLException {
        return 0;
    }

    @Override
    public void markAllAsRead() throws SQLException {

    }

    @Override
    public void markAsRead(@NotNull String identifier) throws SQLException {

    }
}
