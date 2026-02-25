package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.databasent.table.AlertEntity;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

public interface AlertService extends IService<AlertEntity> {
    @NotNull List<AlertEntity> getUnreadAlerts();

    boolean identifierAlertExists(@NotNull String identifier);

    boolean identifierAlertExistsIncludeRead(@NotNull String identifier);

    long deleteOldAlerts(@NotNull OffsetDateTime before);

    void markAllAsRead();

    void markAsRead(@NotNull String identifier);
}
