package com.ghostchu.peerbanhelper.gui.impl.swing;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.gui.impl.GuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.logger.JListAppender;
import com.ghostchu.peerbanhelper.util.logger.LogEntry;
import com.jthemedetecor.OsThemeDetector;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Getter
@Slf4j
public class SwingGuiImpl extends ConsoleGuiImpl implements GuiImpl {
    @Getter
    private final boolean silentStart;
    private MainWindow mainWindow;

    public SwingGuiImpl(String[] args) {
        super(args);
        this.silentStart = Arrays.stream(args).anyMatch(s -> s.equalsIgnoreCase("silent"));
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "PeerBanHelper");
        System.setProperty("apple.awt.application.appearance", "system");
    }

    private void updateTitle() {
        StringBuilder builder = new StringBuilder();
        builder.append(tlUI(Lang.GUI_TITLE_LOADED, "Swing UI", Main.getMeta().getVersion(), Main.getMeta().getAbbrev()));
        StringJoiner joiner = new StringJoiner("", " [", "]");
        joiner.setEmptyValue("");
        ExchangeMap.GUI_DISPLAY_FLAGS.forEach(flag -> joiner.add(flag.getContent()));
        SwingUtilities.invokeLater(() -> {
            if (mainWindow != null) {
                mainWindow.setTitle(builder.append(joiner).toString());
            }
        });
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
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::updateTitle, 0, 1, TimeUnit.SECONDS);
    }

    private void updateTheme(Boolean isDark) {
        if (isDark) {
            FlatDarculaLaf.setup();
        } else {
            FlatIntelliJLaf.setup();
        }
    }

    @Override
    public void createMainWindow() {
        mainWindow = new MainWindow(this);
        initLoggerRedirection();
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
        Thread.ofVirtual().start(() -> {
            while (true) {
                try {
                    var logEntry = JListAppender.logEntryDeque.poll(1, TimeUnit.HOURS);
                    if (logEntry == null) continue;
                    SwingUtilities.invokeLater(() -> {
                        DefaultListModel<LogEntry> model = (DefaultListModel<LogEntry>) logList.getModel();
                        model.addElement(logEntry);

                        // 限制最大元素数量为 500
                        while (model.size() > 300) {
                            model.removeElementAt(0);
                        }

                        // 如果用户在底部，则自动滚动
                        if (autoScroll.get()) {
                            logList.ensureIndexIsVisible(model.getSize() - 1); // 自动滚动到最底部
                        }
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
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
        JOptionPane.showMessageDialog(null, description, title, msgType);
    }

    @Override
    public void createNotification(Level level, String title, String description) {

        var swingTray = mainWindow.getSwingTrayDialog();
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
        var swingTray = mainWindow.getSwingTrayDialog();
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
