package com.ghostchu.peerbanhelper.gui.impl.qt;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelper;
import com.ghostchu.peerbanhelper.event.PBHLookAndFeelNeedReloadEvent;
import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.gui.ProgressDialog;
import com.ghostchu.peerbanhelper.gui.TaskbarControl;
import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.qt.tabs.QtLogsTabComponent;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.PBHFlatLafTheme;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.impl.StandardLafTheme;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.logger.JListAppender;
import com.google.common.eventbus.Subscribe;
import com.jthemedetecor.OsThemeDetector;
import io.qt.core.QMetaObject;
import io.qt.core.QUrl;
import io.qt.core.Qt;
import io.qt.gui.QDesktopServices;
import io.qt.widgets.QApplication;
import io.qt.widgets.QMessageBox;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Getter
@Slf4j
public final class QtGuiImpl extends ConsoleGuiImpl {
    @Getter
    private final boolean silentStart;
    private QtMainWindow qtMainWindow;
    @Getter
    private PBHFlatLafTheme pbhFlatLafTheme = new StandardLafTheme();
    @Getter
    private QtTaskbarControl qtTaskbarControl;
    @Getter
    private QApplication application;

    public QtGuiImpl(String[] args) {
        super(args);
        System.setProperty("java.awt.headless", "true");
        this.silentStart = Arrays.stream(args).anyMatch(s -> s.equalsIgnoreCase("silent"));
        // 设置应用程序属性
        QApplication.setApplicationName("PeerBanHelper");
        QApplication.setApplicationVersion(Main.getMeta().getVersion());
        QApplication.setOrganizationName("PeerBanHelper");
        QApplication.setOrganizationDomain("peerbanhelper.com");
        QApplication.initialize(args);
        // 初始化Qt应用程序
        this.application = QApplication.instance();

    }

    private void updateGuiStuff() {
        StringBuilder builder = new StringBuilder();
        builder.append(tlUI(Lang.GUI_TITLE_LOADED, "Qt6 UI", Main.getMeta().getVersion(), Main.getMeta().getAbbrev()));
        StringJoiner joiner = new StringJoiner("", " [", "]");
        joiner.setEmptyValue("");
        ExchangeMap.GUI_DISPLAY_FLAGS.forEach(flag -> joiner.add(flag.getContent()));
        String finalTitle = builder.append(joiner).toString();
        if (qtMainWindow != null) {
            QMetaObject.invokeMethod(qtMainWindow, () -> qtMainWindow.setWindowTitle(finalTitle), Qt.ConnectionType.QueuedConnection);
        }
    }

    @Override
    public void setup() {
        super.setup();
        createMainWindow(); // 创建主窗口
        Main.getEventBus().register(this);
        try {
            // 主题检测器
            OsThemeDetector detector = OsThemeDetector.getDetector();
            detector.registerListener(this::updateTheme);
            updateTheme(detector.isDark());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void createMainWindow() {
        qtMainWindow = new QtMainWindow(this);
        qtTaskbarControl = new QtTaskbarControl(qtMainWindow);
        initLoggerRedirection();
    }

    private void updateTheme(Boolean isDark) {
        // 在Qt中设置主题
        QMetaObject.invokeMethod(qtMainWindow, () -> {
            if (isDark) {
                // 设置深色主题
                QApplication.instance().setStyleSheet("""
                        QWidget {
                            background-color: #2b2b2b;
                            color: #ffffff;
                        }
                        QTabWidget::pane {
                            border: 1px solid #3c3c3c;
                            background-color: #2b2b2b;
                        }
                        QTabBar::tab {
                            background-color: #3c3c3c;
                            color: #ffffff;
                            padding: 8px 16px;
                            margin-right: 2px;
                            border: 1px solid #555;
                        }
                        QTabBar::tab:selected {
                            background-color: #0078d4;
                        }
                        QTabBar::tab:hover {
                            background-color: #404040;
                        }
                        QMenuBar {
                            background-color: #2b2b2b;
                            color: #ffffff;
                        }
                        QMenuBar::item {
                            background-color: transparent;
                            color: #ffffff;
                            padding: 4px 8px;
                        }
                        QMenuBar::item:selected {
                            background-color: #0078d4;
                        }
                        QMenu {
                            background-color: #2b2b2b;
                            color: #ffffff;
                            border: 1px solid #3c3c3c;
                        }
                        QMenu::item:selected {
                            background-color: #0078d4;
                        }
                        QTableWidget {
                            background-color: #323232;
                            alternate-background-color: #383838;
                            color: #ffffff;
                            gridline-color: #555555;
                            selection-background-color: #0078d4;
                        }
                        QHeaderView::section {
                            background-color: #404040;
                            color: #ffffff;
                            padding: 4px;
                            border: 1px solid #555555;
                        }
                        QToolBar {
                            background-color: #353535;
                            border: 1px solid #555555;
                            spacing: 3px;
                        }
                        QToolBar QToolButton {
                            background-color: transparent;
                            color: #ffffff;
                            border: 1px solid transparent;
                            padding: 3px;
                            margin: 1px;
                        }
                        QToolBar QToolButton:hover {
                            background-color: #404040;
                            border: 1px solid #606060;
                        }
                        QToolBar QToolButton:pressed {
                            background-color: #0078d4;
                        }
                        QScrollBar:vertical {
                            background-color: #404040;
                            width: 15px;
                            margin: 0px;
                        }
                        QScrollBar::handle:vertical {
                            background-color: #606060;
                            min-height: 20px;
                            border-radius: 7px;
                        }
                        QScrollBar::handle:vertical:hover {
                            background-color: #707070;
                        }
                        QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {
                            height: 0px;
                        }
                        """);
            } else {
                // 设置浅色主题
                QApplication.instance().setStyleSheet("""
                        QWidget {
                            background-color: #ffffff;
                            color: #000000;
                        }
                        QTabWidget::pane {
                            border: 1px solid #c0c0c0;
                            background-color: #ffffff;
                        }
                        QTabBar::tab {
                            background-color: #f0f0f0;
                            color: #000000;
                            padding: 8px 16px;
                            margin-right: 2px;
                            border: 1px solid #c0c0c0;
                        }
                        QTabBar::tab:selected {
                            background-color: #0078d4;
                            color: #ffffff;
                        }
                        QTabBar::tab:hover {
                            background-color: #e0e0e0;
                        }
                        QMenuBar {
                            background-color: #ffffff;
                            color: #000000;
                        }
                        QMenuBar::item {
                            background-color: transparent;
                            color: #000000;
                            padding: 4px 8px;
                        }
                        QMenuBar::item:selected {
                            background-color: #0078d4;
                            color: #ffffff;
                        }
                        QMenu {
                            background-color: #ffffff;
                            color: #000000;
                            border: 1px solid #c0c0c0;
                        }
                        QMenu::item:selected {
                            background-color: #0078d4;
                            color: #ffffff;
                        }
                        QTableWidget {
                            background-color: #ffffff;
                            alternate-background-color: #f5f5f5;
                            color: #000000;
                            gridline-color: #d0d0d0;
                            selection-background-color: #0078d4;
                            selection-color: #ffffff;
                        }
                        QHeaderView::section {
                            background-color: #f0f0f0;
                            color: #000000;
                            padding: 4px;
                            border: 1px solid #c0c0c0;
                        }
                        QToolBar {
                            background-color: #f8f8f8;
                            border: 1px solid #c0c0c0;
                            spacing: 3px;
                        }
                        QToolBar QToolButton {
                            background-color: transparent;
                            color: #000000;
                            border: 1px solid transparent;
                            padding: 3px;
                            margin: 1px;
                        }
                        QToolBar QToolButton:hover {
                            background-color: #e0e0e0;
                            border: 1px solid #c0c0c0;
                        }
                        QToolBar QToolButton:pressed {
                            background-color: #0078d4;
                            color: #ffffff;
                        }
                        QScrollBar:vertical {
                            background-color: #f0f0f0;
                            width: 15px;
                            margin: 0px;
                        }
                        QScrollBar::handle:vertical {
                            background-color: #c0c0c0;
                            min-height: 20px;
                            border-radius: 7px;
                        }
                        QScrollBar::handle:vertical:hover {
                            background-color: #a0a0a0;
                        }
                        QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {
                            height: 0px;
                        }
                        """);
            }
        }, Qt.ConnectionType.QueuedConnection);
    }

    @Override
    public String getName() {
        return "Qt";
    }

    @Override
    public boolean supportInteractive() {
        return true;
    }

    @Override
    public void createYesNoDialog(Level level, String title, String description, @Nullable Runnable yesEvent,
                                  @Nullable Runnable noEvent) {
        if (qtMainWindow != null) {
            qtMainWindow.activateWindow();
            qtMainWindow.raise();
        }

        QMetaObject.invokeMethod(qtMainWindow, () -> {
            QMessageBox messageBox = new QMessageBox();
            messageBox.setWindowTitle(title);
            messageBox.setText(description);
            messageBox.setStandardButtons(QMessageBox.StandardButton.Yes, QMessageBox.StandardButton.No);

            if (level == Level.INFO) {
                messageBox.setIcon(QMessageBox.Icon.Information);
            } else if (level == Level.WARNING) {
                messageBox.setIcon(QMessageBox.Icon.Warning);
            } else if (level == Level.SEVERE) {
                messageBox.setIcon(QMessageBox.Icon.Critical);
            }
            int result = messageBox.exec();
            if (result == QMessageBox.StandardButton.Yes.value()) {
                if (yesEvent != null) yesEvent.run();
            } else if (result == QMessageBox.StandardButton.No.value()) {
                if (noEvent != null) noEvent.run();
            }
        }, Qt.ConnectionType.QueuedConnection);
    }

    @Override
    public void onPBHFullyStarted(PeerBanHelper server) {
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::updateGuiStuff, 0, 1, TimeUnit.SECONDS);
        // WebUI标签页已移除，因为QWebEngine未引入
    }

    @Subscribe
    public void needReloadThemes(PBHLookAndFeelNeedReloadEvent event) {
        // 主题重载事件处理
    }

    @Override
    public boolean isGuiAvailable() {
        return true;
    }

    @Override
    public ProgressDialog createProgressDialog(String title, String description, String buttonText,
                                               Runnable buttonEvent, boolean allowCancel) {
        return new QtProgressDialog(title, description, buttonText, buttonEvent, allowCancel);
    }

    @Override
    public TaskbarControl taskbarControl() {
        if (qtTaskbarControl != null) {
            return qtTaskbarControl;
        }
        return super.taskbarControl();
    }

    private void initLoggerRedirection() {
        QtLogsTabComponent logsTabComponent = qtMainWindow.getLogsTabComponent();
        AtomicBoolean autoScroll = new AtomicBoolean(true);
        JListAppender.allowWriteLogEntryDeque.set(true);
        var maxSize = ExternalSwitch.parseInt("pbh.gui.logs.maxSize", 300);
        // 由于Qt Java绑定的重复定时器有问题，使用递归singleShot来实现重复定时器

        CommonUtil.getScheduler().scheduleWithFixedDelay(() -> {
            QMetaObject.invokeMethod(qtMainWindow, () -> {
                while (!JListAppender.logEntryDeque.isEmpty()) {
                    var logEntry = JListAppender.logEntryDeque.poll();
                    if (logEntry == null) break;
                    logsTabComponent.addLogEntry(logEntry.content(), logEntry.level());
                    if (autoScroll.get()) {
                        logsTabComponent.scrollToBottom();
                    }
                }
                logsTabComponent.limitLogEntries(maxSize);
            }, Qt.ConnectionType.QueuedConnection);
        }, 0, 10, TimeUnit.MILLISECONDS);
    }


    @Override
    public void sync() {
        // Qt事件循环在sync()中启动
        QApplication.exec();
    }

    @Override
    public void close() {
        if (application != null) {
            QMetaObject.invokeMethod(qtMainWindow, QApplication::quit, Qt.ConnectionType.QueuedConnection);
        }
    }

    @Override
    public void createDialog(Level level, String title, String description, Runnable clickEvent) {
        if (qtMainWindow != null) {
            qtMainWindow.activateWindow();
            qtMainWindow.raise();
        }
        QMetaObject.invokeMethod(qtMainWindow, () -> {
            QMessageBox messageBox = new QMessageBox();
            messageBox.setWindowTitle(title);
            messageBox.setText(description);
            messageBox.setStandardButtons(QMessageBox.StandardButton.Ok);

            if (level == Level.INFO) {
                messageBox.setIcon(QMessageBox.Icon.Information);
            } else if (level == Level.WARNING) {
                messageBox.setIcon(QMessageBox.Icon.Warning);
            } else if (level == Level.SEVERE) {
                messageBox.setIcon(QMessageBox.Icon.Critical);
            }

            messageBox.exec();
            if (clickEvent != null) {
                clickEvent.run();
            }
        }, Qt.ConnectionType.QueuedConnection);
    }

    @Override
    public void createNotification(Level level, String title, String description) {
        if (qtMainWindow != null && qtMainWindow.getTrayManager() != null) {
            QMetaObject.invokeMethod(qtMainWindow, () -> {
                qtMainWindow.getTrayManager().createNotification(level, title, description);
            }, Qt.ConnectionType.QueuedConnection);
        } else {
            super.createNotification(level, title, description);
        }
    }

    public void openWebpage(URI uri) {
        QMetaObject.invokeMethod(qtMainWindow, () -> {
            QDesktopServices.openUrl(new QUrl(uri.toString()));

        }, Qt.ConnectionType.QueuedConnection);

    }
}
