package com.ghostchu.peerbanhelper.gui.impl.qt;

import com.ghostchu.peerbanhelper.gui.TaskbarControl;
import com.ghostchu.peerbanhelper.gui.TaskbarState;
import io.qt.core.QTimer;
import io.qt.widgets.QMainWindow;
import org.jetbrains.annotations.Nullable;

public class QtTaskbarControl implements TaskbarControl {
    private final QMainWindow mainWindow;

    public QtTaskbarControl(QMainWindow mainWindow) {
        this.mainWindow = mainWindow;
    }

    @Override
    public void updateProgress(@Nullable Object window, TaskbarState state, float progress) {
        QTimer.singleShot(0, () -> {            // Qt中的任务栏进度实现可能需要平台特定的代码
            // 这里提供基本实现
            switch (state) {
                case NORMAL -> {
                    // 正常进度状态
                }
                case INDETERMINATE -> {
                    // 无限进度状态
                }
                case ERROR -> {
                    // 错误状态
                }
                case PAUSED -> {
                    // 暂停状态
                }
                case OFF -> {
                    // 关闭进度显示
                }
            }
        });
    }

    @Override
    public void requestUserAttention(@Nullable Object window, boolean critical) {
        QTimer.singleShot(0, () -> {
            if (mainWindow != null) {
                mainWindow.activateWindow();
                mainWindow.raise();
                if (critical) {
                    // 重要通知时的处理
                    mainWindow.activateWindow();
                }
            }
        });
    }
}
