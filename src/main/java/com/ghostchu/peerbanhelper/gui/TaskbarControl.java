package com.ghostchu.peerbanhelper.gui;

import java.awt.*;

public interface TaskbarControl {
    void updateProgress(Window window, Taskbar.State state, float progress);

    void requestUserAttention(Window window, boolean critical);
}
