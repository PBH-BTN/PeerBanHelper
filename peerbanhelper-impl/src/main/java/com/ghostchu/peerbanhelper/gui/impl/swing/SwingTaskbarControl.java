package com.ghostchu.peerbanhelper.gui.impl.swing;

import com.ghostchu.peerbanhelper.gui.TaskbarControl;
import com.ghostchu.peerbanhelper.gui.TaskbarState;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public final class SwingTaskbarControl implements TaskbarControl {

    private final Window parent;

    public SwingTaskbarControl(Object parent) {
        this.parent = (Window) parent;
    }

    @Override
    public void updateProgress(@Nullable Object window, TaskbarState state, float progress) {
        if (!Taskbar.isTaskbarSupported()) return;
        if (window == null) {
            window = parent;
        }
        @Nullable Window finalWindow = (Window) window;
        EventQueue.invokeLater(() -> {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.PROGRESS_VALUE_WINDOW)) {
                taskbar.setWindowProgressValue(finalWindow, (int) (progress * 100));
            } else if (taskbar.isSupported(Taskbar.Feature.PROGRESS_VALUE)) {
                taskbar.setProgressValue((int) (progress * 100));
            }
            if (taskbar.isSupported(Taskbar.Feature.PROGRESS_STATE_WINDOW)) {
                switch (state) {
                    case INDETERMINATE -> taskbar.setWindowProgressState(finalWindow, Taskbar.State.INDETERMINATE);
                    case NORMAL -> taskbar.setWindowProgressState(finalWindow, Taskbar.State.NORMAL);
                    case ERROR -> taskbar.setWindowProgressState(finalWindow, Taskbar.State.ERROR);
                    case PAUSED -> taskbar.setWindowProgressState(finalWindow, Taskbar.State.PAUSED);
                    case OFF -> taskbar.setWindowProgressState(finalWindow, Taskbar.State.OFF);
                }
            }
        });
    }

    @Override
    public void requestUserAttention(@Nullable Object window, boolean critical) {
        if (!Taskbar.isTaskbarSupported()) return;
        if (window == null) {
            window = parent;
        }
        @Nullable Window finalWindow = (Window) window;
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
