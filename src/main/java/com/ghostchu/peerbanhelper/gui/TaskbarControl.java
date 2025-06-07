package com.ghostchu.peerbanhelper.gui;

public interface TaskbarControl {
    void updateProgress(Object window, TaskbarState state, float progress);

    void requestUserAttention(Object window, boolean critical);
}
