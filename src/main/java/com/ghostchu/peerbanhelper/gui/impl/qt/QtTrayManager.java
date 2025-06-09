package com.ghostchu.peerbanhelper.gui.impl.qt;

import com.ghostchu.peerbanhelper.gui.impl.qt.tabs.QtTabComponent;
import com.ghostchu.peerbanhelper.text.Lang;
import io.qt.core.QMetaObject;
import io.qt.core.Qt;
import io.qt.gui.QAction;
import io.qt.gui.QIcon;
import io.qt.widgets.QApplication;
import io.qt.widgets.QMenu;
import io.qt.widgets.QSystemTrayIcon;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.logging.Level;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class QtTrayManager {
    private final QtMainWindow mainWindow;
    private final QIcon icon;
    private final List<QtTabComponent> tabComponents;
    private QSystemTrayIcon trayIcon;
    private QMenu trayMenu;
    private boolean notificationShown = false; // 跟踪通知是否已经显示过

    public QtTrayManager(QtMainWindow mainWindow, QIcon icon, List<QtTabComponent> tabComponents) {
        this.mainWindow = mainWindow;
        this.icon = icon;
        this.tabComponents = tabComponents;

        initializeTray();
    }

    private void initializeTray() {
        if (!QSystemTrayIcon.isSystemTrayAvailable()) {
            log.warn("System tray is not available");
            return;
        }

        try {
            trayIcon = new QSystemTrayIcon();
            if (icon != null) {
                trayIcon.setIcon(icon);
                log.info("Tray icon set successfully");
            } else {
                log.warn("Tray icon is null");
            }

            createTrayMenu();
            trayIcon.setContextMenu(trayMenu);

            // 设置工具提示
            trayIcon.setToolTip("PeerBanHelper");

            // 连接激活信号
            trayIcon.activated.connect(this::onTrayIconActivated);

            // 显示托盘图标
            trayIcon.show();

            log.info("System tray icon initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize system tray", e);
        }
    }

    private void createTrayMenu() {
        trayMenu = new QMenu();
        // 显示/隐藏主窗口
        QAction showHideAction = new QAction(tlUI(Lang.GUI_MENU_SHOW_WINDOW));
        showHideAction.triggered.connect(() -> {
            if (mainWindow.isVisible()) {
                mainWindow.hide();
            } else {
                mainWindow.show();
                mainWindow.activateWindow();
                mainWindow.raise();
            }
        });
        trayMenu.addAction(showHideAction);

        trayMenu.addSeparator();

        // 添加Tab组件的快捷菜单项
        for (QtTabComponent component : tabComponents) {
            if (component.getTrayMenuActions() != null) {
                for (QAction action : component.getTrayMenuActions()) {
                    trayMenu.addAction(action);
                }
            }
        }

        trayMenu.addSeparator();

        // 退出
        QAction quitAction = new QAction(tlUI(Lang.GUI_MENU_QUIT));
        quitAction.triggered.connect(() -> {
            trayIcon.hide();
            QApplication.quit();
        });
        trayMenu.addAction(quitAction);
    }

    private void onTrayIconActivated(QSystemTrayIcon.ActivationReason reason) {
        switch (reason) {
            case DoubleClick -> {
                // 双击显示/隐藏主窗口
                if (mainWindow.isVisible()) {
                    mainWindow.hide();
                } else {
                    mainWindow.show();
                    mainWindow.activateWindow();
                    mainWindow.raise();
                }
            }
            case Trigger -> {
                // 单击可以显示上下文菜单或其他操作
                log.debug("Tray icon clicked");
            }
            case MiddleClick, Context, Unknown -> {
                // 其他激活方式，暂时不处理
                log.debug("Tray icon activated with reason: {}", reason);
            }
        }
    }

    public void createNotification(Level level, String title, String description) {
        if (trayIcon == null || !trayIcon.isVisible()) {
            log.warn("Tray icon not available for notification");
            return;
        }

        QSystemTrayIcon.MessageIcon messageIcon;
        switch (level.intValue()) {
            case 1000 -> messageIcon = QSystemTrayIcon.MessageIcon.Critical; // SEVERE
            case 900 -> messageIcon = QSystemTrayIcon.MessageIcon.Warning;   // WARNING
            default -> messageIcon = QSystemTrayIcon.MessageIcon.Information; // INFO and others
        }

        try {
            QMetaObject.invokeMethod(mainWindow, () -> {
                trayIcon.showMessage(title, description, messageIcon, 5000); // 显示5秒
            }, Qt.ConnectionType.QueuedConnection);
        } catch (Exception e) {
            log.error("Failed to show tray notification", e);
        }
    }

    /**
     * 最小化到托盘
     */
    public void minimizeToTray() {
        QMetaObject.invokeMethod(mainWindow, () -> {
            if (trayIcon != null) {
                mainWindow.hide();
                if (!notificationShown) {
                    notificationShown = true;
                    createNotification(Level.INFO,
                            tlUI(Lang.GUI_TRAY_MESSAGE_CAPTION),
                            tlUI(Lang.GUI_TRAY_MESSAGE_DESCRIPTION));
                }
            }
        }, Qt.ConnectionType.QueuedConnection);
    }

    public void dispose() {
        QMetaObject.invokeMethod(mainWindow, () -> {
            if (trayIcon != null) {
                trayIcon.hide();
                trayIcon = null;
            }
            if (trayMenu != null) {
                trayMenu = null;
            }
        }, Qt.ConnectionType.QueuedConnection);
    }
}
