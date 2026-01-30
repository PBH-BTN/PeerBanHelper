package com.ghostchu.peerbanhelper.gui.impl.swing;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelper;
import com.ghostchu.peerbanhelper.event.gui.PBHGuiElementPeriodUpdateEvent;
import com.ghostchu.peerbanhelper.event.gui.PBHLookAndFeelNeedReloadEvent;
import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.gui.ProgressDialog;
import com.ghostchu.peerbanhelper.gui.TaskbarControl;
import com.ghostchu.peerbanhelper.gui.TaskbarState;
import com.ghostchu.peerbanhelper.gui.impl.GuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.SwingMainWindow;
import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component.LogsTab;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.PBHFlatLafTheme;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.impl.MacOSLafTheme;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.impl.PBHPlusTheme;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.impl.StandardLafTheme;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.impl.UnsupportedPlatformTheme;
import com.ghostchu.peerbanhelper.gui.impl.swing.toolwindow.SwingProgressDialog;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.logger.JListAppender;
import com.ghostchu.peerbanhelper.util.logger.LogEntry;
import com.jthemedetecor.OsThemeDetector;
import io.sentry.Sentry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.net.URI;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Slf4j
public final class SwingGuiImpl extends ConsoleGuiImpl implements GuiImpl {
    @Getter
    private final boolean silentStart;
    private SwingMainWindow mainWindow;
    @Getter
    private PBHFlatLafTheme pbhFlatLafTheme = new StandardLafTheme();
    @Getter
    private SwingTaskbarControl swingTaskbarControl;

    private final Icon icon = new FlatSVGIcon(Main.class.getResource("/assets/icon/common/alert.svg"));

    public SwingGuiImpl(String[] args) {
        super(args);
        this.silentStart = Arrays.stream(args).anyMatch(s -> s.equalsIgnoreCase("silent"));
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "PeerBanHelper");
        System.setProperty("apple.awt.application.appearance", "system");
        // is linux?
        if (System.getProperty("os.name").contains("Linux")) {
            JFrame.setDefaultLookAndFeelDecorated(true);
            JDialog.setDefaultLookAndFeelDecorated(true);
        }
    }

    @Override
    public boolean isGuiAvailable() {
        return Desktop.isDesktopSupported();
    }

    private void setUIFont(String fontName) {
        Enumeration<Object> keys = UIManager.getLookAndFeelDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource fontUIResource)
                UIManager.put(key, getFont(fontName, -1, -1, fontUIResource));
        }
    }

    private void updateGuiStuff() {
        // taskbar
        if (Main.getServer().getDownloaderServer().isGlobalPaused()) {
            taskbarControl().updateProgress(mainWindow, TaskbarState.PAUSED, 1.0f);
        } else {
            taskbarControl().updateProgress(mainWindow, TaskbarState.OFF, -1.0f);
        }

        Main.getEventBus().post(new PBHGuiElementPeriodUpdateEvent());
    }


    @Override
    public String getName() {
        return "SWING";
    }

    @Override
    public boolean supportInteractive() {
        return true;
    }

    @Override
    public void createYesNoDialog(Level level, String title, String description, @Nullable Runnable yesEvent, @Nullable Runnable noEvent) {
        int msgType = JOptionPane.PLAIN_MESSAGE;
        if (level == Level.INFO) {
            msgType = JOptionPane.INFORMATION_MESSAGE;
        }
        if (level == Level.WARN) {
            msgType = JOptionPane.WARNING_MESSAGE;
        }
        if (level == Level.ERROR) {
            msgType = JOptionPane.ERROR_MESSAGE;
        }
        if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.USER_ATTENTION_WINDOW)) {
            Taskbar.getTaskbar().requestWindowUserAttention(mainWindow);
        }
        var finalMsgType = msgType;
        SwingUtilities.invokeLater(() -> {
            int result = JOptionPane.showOptionDialog(null, description, title,
                    JOptionPane.YES_NO_OPTION, finalMsgType, null, null, JOptionPane.NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                if (yesEvent != null) yesEvent.run();
            } else if (result == JOptionPane.NO_OPTION) {
                if (noEvent != null) noEvent.run();
            }
        });
    }

    @Override
    public void openUrlInBrowser(String url) {
        openWebpage(URI.create(url));
    }

    @Override
    public void setup() {
        super.setup();
        //FlatIntelliJLaf.setup();
        setupSwingDefaultFonts();
        Main.getEventBus().register(this);
        try {
            // 这玩意儿能空指针？
            OsThemeDetector detector = OsThemeDetector.getDetector();
            detector.registerListener(this::updateTheme);
            updateTheme(detector.isDark());
        } catch (Exception e) {
            Sentry.captureException(e);
        }
        createMainWindow();
    }

    @Override
    public boolean isDarkMode() {
        return OsThemeDetector.getDetector().isDark();
    }

    private void setupSwingDefaultFonts() {
//        FontUIResource fontRes = new FontUIResource(new Font("Microsoft YaHei UI" , Font.PLAIN, 16));
//        for (Enumeration<Object> keys = UIManager.getDefaults().keys(); keys.hasMoreElements();) {
//            Object key = keys.nextElement();
//            Object value = UIManager.get(key);
//            if (value instanceof FontUIResource) {
//                UIManager.put(key, fontRes);
//            }
//        }

    }

    @Override
    public void onPBHFullyStarted(PeerBanHelper server) {
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::updateGuiStuff, 0, 1, TimeUnit.SECONDS);
    }


    private void updateTheme(Boolean isDark) {
        pbhFlatLafTheme = new StandardLafTheme();
        //macos?
        if (ExternalSwitch.parseBoolean("pbh.gui.macos-theme", true) && System.getProperty("os.name").contains("Mac")) {
            pbhFlatLafTheme = new MacOSLafTheme();
        }
        //PBHPlus?
        if (ExternalSwitch.parseBoolean("pbh.gui.pbhplus-theme", false) && ExchangeMap.PBH_PLUS_ACTIVATED) {
            pbhFlatLafTheme = new PBHPlusTheme();
        }
//        // Snapshot?
//        if (ExternalSwitch.parseBoolean("pbh.gui.insider-theme", true) && Main.getMeta().isSnapshotOrBeta() || "LiveDebug".equalsIgnoreCase(ExternalSwitch.parse("pbh.release"))) {
//            pbhFlatLafTheme = new SnapshotTheme();
//        }
        // Unsupported platform?
        if (ExchangeMap.UNSUPPORTED_PLATFORM && ExternalSwitch.parseBoolean("pbh.gui.useIncompatiblePlatformTheme", false)) {
            pbhFlatLafTheme = new UnsupportedPlatformTheme();
        }
        // Customized?
        if (ExternalSwitch.parse("pbh.gui.theme-light") != null && ExternalSwitch.parse("pbh.gui.theme-dark") != null) {
            pbhFlatLafTheme = new PBHFlatLafTheme() {
                @Override
                public void applyDark() {
                    try {
                        UIManager.setLookAndFeel(ExternalSwitch.parse("pbh.gui.theme-dark"));
                    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                             UnsupportedLookAndFeelException e) {
                        log.error("Failed to apply user customized dark theme", e);
                    }
                }

                @Override
                public void applyLight() {
                    try {
                        UIManager.setLookAndFeel(ExternalSwitch.parse("pbh.gui.theme-light"));
                    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                             UnsupportedLookAndFeelException e) {
                        log.error("Failed to apply user customized light theme", e);
                    }
                }
            };
        }

        if (isDark) {
            pbhFlatLafTheme.applyDark();
        } else {
            pbhFlatLafTheme.applyLight();
        }
        Main.getEventBus().post(new PBHLookAndFeelNeedReloadEvent(isDark));
        FlatLaf.updateUILater();
    }

    /**
     * @noinspection ALL
     */
    private Font getFont(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
        boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
        Font fontWithFallback = isMac ? new Font(font.getFamily(), font.getStyle(), font.getSize()) : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
        return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
    }

    @Override
    public void createMainWindow() {
        mainWindow = new SwingMainWindow(this);
        swingTaskbarControl = new SwingTaskbarControl(mainWindow);
        initLoggerRedirection();
    }

    @Override
    public ProgressDialog createProgressDialog(String title, String description, String buttonText, Runnable buttonEvent, boolean allowCancel) {
        return new SwingProgressDialog(title, description, buttonText, buttonEvent, allowCancel);
    }

    @Override
    public TaskbarControl taskbarControl() {
        if (swingTaskbarControl != null) {
            return swingTaskbarControl;
        }
        return super.taskbarControl();
    }

    public boolean openWebpage(URI uri) {
        Desktop desktop = Desktop.getDesktop();
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            try {
                desktop.browse(uri);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                Sentry.captureException(e);
            }
        }
        return false;
    }

    private void initLoggerRedirection() {
        JScrollPane scrollPane = mainWindow.getTab(LogsTab.class).getLoggerScrollPane();  // 获取 JList 所在的 JScrollPane
        JList<LogEntry> logList = mainWindow.getTab(LogsTab.class).getLoggerTextList();
        BoundedRangeModel scrollModel = scrollPane.getVerticalScrollBar().getModel();

        // 用于追踪用户是否在最底部
        AtomicBoolean autoScroll = new AtomicBoolean(true);

        // 监听滚动条的变化，判断用户是否在最底部
        scrollModel.addChangeListener(e -> {
            int max = scrollModel.getMaximum();
            int extent = scrollModel.getExtent();
            int current = scrollModel.getValue();

            // 如果滚动条到达底部，启用自动滚动
            // 如果用户滚动到了其他位置，禁用自动滚动
            autoScroll.set(current + extent == max);
        });

        var maxSize = ExternalSwitch.parseInt("pbh.gui.logs.maxSize", 300);

        JListAppender.allowWriteLogEntryDeque.set(true);
        CommonUtil.getScheduler().scheduleWithFixedDelay(() -> SwingUtilities.invokeLater(() -> {
            DefaultListModel<LogEntry> model = (DefaultListModel<LogEntry>) logList.getModel();
            while (!JListAppender.logEntryDeque.isEmpty()) {
                var logEntry = JListAppender.logEntryDeque.poll();
                if (logEntry == null) return;
                model.addElement(logEntry);
            }
            // 限制最大元素数量为 500
            while (model.size() > maxSize) {
                model.removeElementAt(0);
            }
            if (autoScroll.get()) {
                logList.ensureIndexIsVisible(model.getSize() - 1); // 自动滚动到最底部
            }
        }), 0, 10, TimeUnit.MILLISECONDS);
    }


    @Override
    public void sync() {
        super.sync();
    }

    public void setColorTheme(Class<?> clazz) {
        if (clazz.getName().equals(UIManager.getLookAndFeel().getClass().getName()))
            return;
        try {
            UIManager.setLookAndFeel(clazz.getName());
        } catch (Exception ex) {
            log.info("Failed to setup UI theme", ex);
            Sentry.captureException(ex);
        }
        // FlatLaf.updateUI();
    }

    @Override
    public void close() {
    }

    @Override
    public void createDialog(Level level, String title, String description, Runnable clickEvent) {
        int msgType = JOptionPane.PLAIN_MESSAGE;
        if (level == Level.INFO) {
            msgType = JOptionPane.INFORMATION_MESSAGE;
        }
        if (level == Level.WARN) {
            msgType = JOptionPane.WARNING_MESSAGE;
        }
        if (level == Level.ERROR) {
            msgType = JOptionPane.ERROR_MESSAGE;
        }
        if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.USER_ATTENTION_WINDOW)) {
            Taskbar.getTaskbar().requestWindowUserAttention(mainWindow);
        }
        var finalMsgType = msgType;
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, description, title, finalMsgType);
            clickEvent.run();
        });
    }

    @Override
    public void createNotification(Level level, String title, String description) {
        var swingTray = mainWindow.getTrayMenu().getSwingTrayDialog();
        if (swingTray != null) {
            var icon = swingTray.getTrayIcon();
            if (swingTray.getTrayIcon() != null) {
                if (level.equals(Level.WARN)) {
                    icon.displayMessage(title, description, TrayIcon.MessageType.WARNING);
                } else if (level.equals(Level.ERROR)) {
                    icon.displayMessage(title, description, TrayIcon.MessageType.ERROR);
                } else {
                    icon.displayMessage(title, description, TrayIcon.MessageType.INFO);
                }
                return;
            }
        }
        super.createNotification(level, title, description);
    }
}
