package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTaskStatus;

public record BackgroundTaskMetaDTO(
        String id,
        BackgroundTaskStatus status,
        boolean progressIndeterminate,
        double progress,
        String title,
        String message,
        boolean cancellable,
        long startedAt,
        long endedAt
) {

}
