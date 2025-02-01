package com.ghostchu.peerbanhelper.gui.impl.swing;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.event.PBHLookAndFeelNeedReloadEvent;
import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.gui.ProgressDialog;
import com.ghostchu.peerbanhelper.gui.TaskbarControl;
import com.ghostchu.peerbanhelper.gui.impl.GuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.PBHFlatLafTheme;
import com.ghostchu.peerbanhelper.gui.impl.swing.theme.impl.*;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.logger.JListAppender;
import com.ghostchu.peerbanhelper.util.logger.LogEntry;
import com.google.common.eventbus.Subscribe;
import com.jthemedetecor.OsThemeDetector;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Getter
@Slf4j
public final class SwingGuiImpl extends ConsoleGuiImpl implements GuiImpl {
    @Getter
    private final boolean silentStart;
    private MainWindow mainWindow;
    @Getter
    private PBHFlatLafTheme pbhFlatLafTheme = new StandardLafTheme();
    @Getter
    private SwingTaskbarControl swingTaskbarControl;

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
        StringBuilder builder = new StringBuilder();
        builder.append(tlUI(Lang.GUI_TITLE_LOADED, Main.getMeta().getVersion(), Main.getMeta().getAbbrev()));
        StringJoiner joiner = new StringJoiner("", " [", "]");
        joiner.setEmptyValue("");
        ExchangeMap.GUI_DISPLAY_FLAGS.forEach(flag -> joiner.add(flag.getContent()));
        SwingUtilities.invokeLater(() -> {
            if (mainWindow != null) {
                mainWindow.setTitle(builder.append(joiner).toString());
            }
        });

        // taskbar
        if (Main.getServer().isGlobalPaused()) {
            taskbarControl().updateProgress(mainWindow, Taskbar.State.PAUSED, 1.0f);
        } else {
            taskbarControl().updateProgress(mainWindow, Taskbar.State.OFF, -1.0f);
        }
    }


    @Override
    public void setup() {
        super.setup();
        //FlatIntelliJLaf.setup();
        setupSwingDefaultFonts();
        Main.getEventBus().register(this);
        OsThemeDetector detector = OsThemeDetector.getDetector();
        detector.registerListener(this::updateTheme);
        updateTheme(detector.isDark());
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
    public void onPBHFullyStarted(PeerBanHelperServer server) {
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::updateGuiStuff, 0, 1, TimeUnit.SECONDS);
    }

    @Subscribe
    public void needReloadThemes(PBHLookAndFeelNeedReloadEvent event) {
        updateTheme(OsThemeDetector.getDetector().isDark());
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
        // Snapshot?
        if (ExternalSwitch.parseBoolean("pbh.gui.insider-theme", true) && Main.getMeta().isSnapshotOrBeta() || "LiveDebug".equalsIgnoreCase(ExternalSwitch.parse("pbh.release"))) {
            pbhFlatLafTheme = new SnapshotTheme();
        }
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
        FlatAnimatedLafChange.showSnapshot();
        try {
            if (isDark) {
                pbhFlatLafTheme.applyDark();
            } else {
                pbhFlatLafTheme.applyLight();
            }
            FlatLaf.updateUILater();
        } finally {
            FlatAnimatedLafChange.hideSnapshotWithAnimation();
        }
    }

    /** @noinspection ALL */
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
        mainWindow = new MainWindow(this);
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
            }
        }
        return false;
    }

    private void initLoggerRedirection() {
        JScrollPane scrollPane = mainWindow.getLoggerScrollPane();  // 获取 JList 所在的 JScrollPane
        JList<LogEntry> logList = mainWindow.getLoggerTextList();
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

        // 日志插入线程
        new Thread(() -> {
            JListAppender.allowWriteLogEntryDeque.set(true);
            while (true) {
                try {
                    var logEntry = JListAppender.logEntryDeque.poll(1, TimeUnit.HOURS);
                    if (logEntry == null) continue;
                    SwingUtilities.invokeAndWait(() -> {
                        DefaultListModel<LogEntry> model = (DefaultListModel<LogEntry>) logList.getModel();
                        model.addElement(logEntry);
                        // 限制最大元素数量为 500
                        while (model.size() > ExternalSwitch.parseInt("pbh.gui.logs.maxSize", 300)) {
                            model.removeElementAt(0);
                        }
                        // 如果用户在底部，则自动滚动
                        if (autoScroll.get()) {
                            logList.ensureIndexIsVisible(model.getSize() - 1); // 自动滚动到最底部
                        }
                    });
                } catch (InterruptedException | InvocationTargetException e) {
                    log.warn("Failed to update log list", e);
                }
            }
        }).start();
    }


    @Override
    public void sync() {
        mainWindow.sync();
        super.sync();
    }

    public void setColorTheme(Class<?> clazz) {
        if (clazz.getName().equals(UIManager.getLookAndFeel().getClass().getName()))
            return;
        try {
            UIManager.setLookAndFeel(clazz.getName());
        } catch (Exception ex) {
            log.info("Failed to setup UI theme", ex);
        }
        // FlatLaf.updateUI();
    }

    @Override
    public void close() {
    }

    @Override
    public void createDialog(Level level, String title, String description) {
        int msgType = JOptionPane.PLAIN_MESSAGE;
        if (level == Level.INFO) {
            msgType = JOptionPane.INFORMATION_MESSAGE;
        }
        if (level == Level.WARNING) {
            msgType = JOptionPane.WARNING_MESSAGE;
        }
        if (level == Level.SEVERE) {
            msgType = JOptionPane.ERROR_MESSAGE;
        }
        if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.USER_ATTENTION_WINDOW)) {
            Taskbar.getTaskbar().requestWindowUserAttention(mainWindow);
        }
        var finalMsgType = msgType;
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, description, title, finalMsgType));
    }

    @Override
    public void createNotification(Level level, String title, String description) {
        var swingTray = mainWindow.getTrayMenu().getSwingTrayDialog();
        if (swingTray != null) {
            var icon = swingTray.getTrayIcon();
            if (swingTray.getTrayIcon() != null) {
                if (level.equals(Level.WARNING)) {
                    icon.displayMessage(title, description, TrayIcon.MessageType.WARNING);
                } else if (level.equals(Level.SEVERE)) {
                    icon.displayMessage(title, description, TrayIcon.MessageType.ERROR);
                } else {
                    icon.displayMessage(title, description, TrayIcon.MessageType.INFO);
                }
                if (System.getProperty("os.name").contains("Windows")) {
                    CommonUtil.getScheduler().schedule(this::refreshTrayIcon, 5, TimeUnit.SECONDS);
                }
                return;
            }
        }
        super.createNotification(level, title, description);
    }

    private synchronized void refreshTrayIcon() {
        var swingTray = mainWindow.getTrayMenu().getSwingTrayDialog();
        if (swingTray != null) {
            var icon = swingTray.getTrayIcon();
            if (icon != null) {
                try {
                    SystemTray tray = SystemTray.getSystemTray();
                    tray.remove(icon); // fix https://github.com/PBH-BTN/PeerBanHelper/issues/515
                    tray.add(icon);
                } catch (AWTException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
