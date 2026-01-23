package com.ghostchu.peerbanhelper.databasent.service;

import com.ghostchu.peerbanhelper.databasent.table.AlertEntity;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

public interface AlertService {
    @NotNull List<AlertEntity> getUnreadAlertsUnPaged() throws SQLException;

    boolean identifierAlertExists(@NotNull String identifier) throws SQLException;

    boolean identifierAlertExistsIncludeRead(@NotNull String identifier) throws SQLException;

    int deleteOldAlerts(@NotNull OffsetDateTime before) throws SQLException;

    void markAllAsRead() throws SQLException;

    void markAsRead(@NotNull String identifier) throws SQLException;
}
