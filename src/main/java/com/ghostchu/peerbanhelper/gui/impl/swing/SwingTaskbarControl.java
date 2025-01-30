package com.ghostchu.peerbanhelper.gui.impl.swing;

import com.ghostchu.peerbanhelper.gui.TaskbarControl;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public final class SwingTaskbarControl implements TaskbarControl {

    private final Window parent;

    public SwingTaskbarControl(Window parent) {
        this.parent = parent;
    }

    @Override
    public void updateProgress(@Nullable Window window, Taskbar.State state, float progress) {
        if (!Taskbar.isTaskbarSupported()) return;
        if (window == null) {
            window = parent;
        }
        @Nullable Window finalWindow = window;
        EventQueue.invokeLater(() -> {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.PROGRESS_VALUE_WINDOW)) {
                taskbar.setWindowProgressValue(finalWindow, (int) (progress * 100));
            } else if (taskbar.isSupported(Taskbar.Feature.PROGRESS_VALUE)) {
                taskbar.setProgressValue((int) (progress * 100));
            }
            if (taskbar.isSupported(Taskbar.Feature.PROGRESS_STATE_WINDOW)) {
                taskbar.setWindowProgressState(finalWindow, state);
            }
        });
    }

    @Override
    public void requestUserAttention(@Nullable Window window, boolean critical) {
        if (!Taskbar.isTaskbarSupported()) return;
        if (window == null) {
            window = parent;
        }
        @Nullable Window finalWindow = window;
        EventQueue.invokeLater(() -> {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.USER_ATTENTION_WINDOW)) {
                taskbar.requestWindowUserAttention(finalWindow);
            } else if (taskbar.isSupported(Taskbar.Feature.USER_ATTENTION)) {
                taskbar.requestUserAttention(true, critical);
            }
        });
    }

}
