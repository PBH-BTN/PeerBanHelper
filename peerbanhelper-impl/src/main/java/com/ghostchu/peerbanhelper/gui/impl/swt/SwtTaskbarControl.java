package com.ghostchu.peerbanhelper.gui.impl.swt;

import com.ghostchu.peerbanhelper.gui.TaskbarControl;
import com.ghostchu.peerbanhelper.gui.TaskbarState;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TaskBar;
import org.eclipse.swt.widgets.TaskItem;
import org.jetbrains.annotations.Nullable;

public final class SwtTaskbarControl implements TaskbarControl {
    private final Shell parent;
    private final Display display;
    private TaskItem taskItem;

    public SwtTaskbarControl(Object parent, Display display) {
        this.parent = (Shell) parent;
        this.display = display;
        initializeTaskItem();
    }

    private void initializeTaskItem() {
        display.syncExec(() -> {
            TaskBar taskBar = display.getSystemTaskBar();
            if (taskBar != null) {
                taskItem = taskBar.getItem(parent);
                if (taskItem == null) {
                    taskItem = taskBar.getItem(null);
                }
            }
        });
    }

    @Override
    public void updateProgress(@Nullable Object window, TaskbarState state, float progress) {
        if (taskItem == null) return;

        display.asyncExec(() -> {
            if (taskItem.isDisposed()) return;

            // 设置进度值（0-100）
            if (progress >= 0) {
                taskItem.setProgress((int) (progress * 100));
            } else {
                taskItem.setProgress(-1); // 不确定状态或无进度
            }

            // 设置状态
            switch (state) {
                case INDETERMINATE -> taskItem.setProgressState(SWT.INDETERMINATE);
                case NORMAL -> taskItem.setProgressState(SWT.NORMAL);
                case ERROR -> taskItem.setProgressState(SWT.ERROR);
                case PAUSED -> taskItem.setProgressState(SWT.PAUSED);
                case OFF -> taskItem.setProgressState(SWT.NORMAL); // SWT没有直接对应的OFF状态，使用NORMAL
            }
        });
    }

    @Override
    public void requestUserAttention(@Nullable Object window, boolean critical) {
        if (taskItem == null) return;

        display.asyncExec(() -> {
            if (taskItem.isDisposed()) return;
            taskItem.setProgressState(critical ? SWT.ERROR : SWT.INDETERMINATE);

            // 在SWT中实现闪烁效果
            Shell shell = window != null ? (Shell) window : parent;
            if (!shell.isDisposed() && !shell.isVisible()) {
                shell.setVisible(true);
            }
            shell.forceActive();
        });
    }
}
