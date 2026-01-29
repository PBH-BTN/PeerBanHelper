package com.ghostchu.peerbanhelper.util.backgroundtask;

import lombok.Getter;

@Getter
public enum BackgroundTaskStatus {
    QUEUED(false),
    PREPARING(true),
    RUNNING(true),
    COMPLETED(false),
    FAILED(false),
    PAUSED(true),
    CANCELLED(false);

    private boolean active;

    BackgroundTaskStatus(boolean active) {
        this.active = active;
    }

}
