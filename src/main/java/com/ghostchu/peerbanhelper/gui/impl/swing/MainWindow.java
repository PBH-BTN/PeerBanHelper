package com.ghostchu.peerbanhelper.gui.impl.swing;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.SystemInfo;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.logger.LogEntry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class MainWindow extends JFrame {
    private final SwingGuiImpl swingGUI;
    private JPanel mainPanel;
    private JTabbedPane tabbedPane;
    private JPanel tabbedPaneLogs;
    @Getter
    private JList<LogEntry> loggerTextList;
    @Getter
    private JScrollPane loggerScrollPane;
    @Nullable
    @Getter
    private SwingTray swingTrayDialog;
    private boolean persistFlagTrayMessageSent;

    public MainWindow(SwingGuiImpl swingGUI) {
        this.swingGUI = swingGUI;
        if (SystemInfo.isMacFullWindowContentSupported)
            getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        setTitle(tlUI(Lang.GUI_TITLE_LOADING, "Swing UI", Main.getMeta().getVersion(), Main.getMeta().getAbbrev()));
        setJMenuBar(setupMenuBar());
        setSize(1000, 600);
        setContentPane(mainPanel);
        setupTabbedPane();
        setupSystemTray();
        setComponents();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                minimizeToTray();
            }
        });
        ImageIcon imageIcon = new ImageIcon(Main.class.getResource("/assets/icon.png"));
        setIconImage(imageIcon.getImage());
        setVisible(!swingGUI.isSilentStart());
        loggerTextList.setModel(new DefaultListModel<>());
        loggerTextList.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        loggerTextList.setCellRenderer(new LogEntryRenderer());
        loggerTextList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        loggerTextList.setLayoutOrientation(JList.VERTICAL);
        loggerTextList.setFixedCellHeight(-1);
    }

    public static void setTabTitle(JPanel tab, String title) {
        JTabbedPane tabbedPane = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, tab);
        for (int tabIndex = 0; tabIndex < tabbedPane.getTabCount(); tabIndex++) {
            if (SwingUtilities.isDescendingFrom(tab, tabbedPane.getComponentAt(tabIndex))) {
                tabbedPane.setTitleAt(tabIndex, title);
                break;
            }
        }
    }

    public static void copyText(String content) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        if (Main.getServer() != null && Main.getServer().getWebContainer() != null) {
            Transferable ts = new StringSelection(content);
            clipboard.setContents(ts, null);
        }
    }

    private void minimizeToTray() {
        if (swingTrayDialog != null) {
            setVisible(false);
            if (!persistFlagTrayMessageSent) {
                persistFlagTrayMessageSent = true;
                swingGUI.createNotification(Level.INFO, tlUI(Lang.GUI_TRAY_MESSAGE_CAPTION), tlUI(Lang.GUI_TRAY_MESSAGE_DESCRIPTION));
            }
        }
    }

    private void setComponents() {

    }

    private void setupSystemTray() {
        if (SystemTray.isSupported()) {
            TrayIcon icon = new TrayIcon(Toolkit.getDefaultToolkit().getImage(Main.class.getResource("/assets/icon.png")));
            icon.setImageAutoSize(true);
            SystemTray sysTray = SystemTray.getSystemTray();//获取系统托盘
            try {
                var tray = new SwingTray(icon, mouseEvent -> setVisible(true), mouseEvent -> updateTrayMenus());
                sysTray.add(icon);//将托盘图表添加到系统托盘
                updateTrayMenus();
                this.swingTrayDialog = tray;
            } catch (AWTException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void updateTrayMenus() {
        if (this.swingTrayDialog == null) return;
        List<JMenuItem> items = new ArrayList<>();
        JMenuItem openMainWindow = new JMenuItem(tlUI(Lang.GUI_MENU_SHOW_WINDOW), new FlatSVGIcon(Main.class.getResource("/assets/icon/tray/open.svg")));
        JMenuItem openWebUI = new JMenuItem(tlUI(Lang.GUI_MENU_WEBUI_OPEN), new FlatSVGIcon(Main.class.getResource("/assets/icon/tray/browser.svg")));
        JMenuItem quit = new JMenuItem(tlUI(Lang.GUI_MENU_QUIT), new FlatSVGIcon(Main.class.getResource("/assets/icon/tray/close.svg")));
        openMainWindow.addActionListener(e -> setVisible(true));
        openWebUI.addActionListener(e -> openWebUI());
        quit.addActionListener(e -> System.exit(0));
        items.add(menuDisplayItem(new JMenuItem(tlUI(Lang.GUI_MENU_STATS))));
        items.add(menuBanStats());
        items.add(menuDownloaderStats());
        items.add(menuDisplayItem(new JMenuItem(tlUI(Lang.GUI_MENU_QUICK_OPERATIONS))));
        items.add(openMainWindow);
        items.add(openWebUI);
        items.add(null);
        items.add(quit);
        this.swingTrayDialog.set(items);
    }

    private JMenuItem menuDownloaderStats() {
        var totalDownloaders = 0L;
        var healthDownloaders = 0L;
        if (Main.getServer() != null) {
            totalDownloaders = Main.getServer().getDownloaders().size();
            healthDownloaders = Main.getServer().getDownloaders().stream().filter(m -> m.getLastStatus() == DownloaderLastStatus.HEALTHY).count();
        }
        return new JMenuItem(tlUI(Lang.GUI_MENU_STATS_DOWNLOADER, healthDownloaders, totalDownloaders), new FlatSVGIcon(Main.class.getResource("/assets/icon/tray/connection.svg")));
    }

    private JMenuItem menuBanStats() {
        var bannedPeers = 0L;
        var bannedIps = 0L;
        var server = Main.getServer();
        if (server != null) {
            bannedIps = Main.getServer().getBannedPeers().values().stream().map(m -> m.getPeer().getAddress().getIp()).distinct().count();
            bannedPeers = Main.getServer().getBannedPeers().values().size();
        }
        return new JMenuItem(tlUI(Lang.GUI_MENU_STATS_BANNED, bannedPeers, bannedIps), new FlatSVGIcon(Main.class.getResource("/assets/icon/tray/banned.svg")));
    }

    private JMenuItem menuDisplayItem(JMenuItem jMenuItem) {
        jMenuItem.setEnabled(false);
        return jMenuItem;
    }

    private JMenuBar setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(generateProgramMenu());
        menuBar.add(generateWebUIMenu());
        this.add(menuBar, BorderLayout.NORTH);
        return menuBar;
    }

    private Component generateProgramMenu() {
        JMenu menu = new JMenu(tlUI(Lang.GUI_MENU_PROGRAM));
        JMenuItem openDataDirectory = new JMenuItem(tlUI(Lang.GUI_MENU_OPEN_DATA_DIRECTORY));
        openDataDirectory.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(Main.getDataDirectory());
            } catch (IOException ex) {
                log.warn("Unable to open data directory {} in desktop env.", Main.getDataDirectory().getPath());
            }
        });
        menu.add(openDataDirectory);
        JMenuItem viewOnGithub = new JMenuItem(tlUI(Lang.ABOUT_VIEW_GITHUB));
        viewOnGithub.addActionListener(e -> swingGUI.openWebpage(URI.create(tlUI(Lang.GITHUB_PAGE))));
        menu.add(viewOnGithub);
        menu.addSeparator();
        JMenuItem quit = new JMenuItem(tlUI(Lang.GUI_MENU_QUIT));
        quit.addActionListener(e -> System.exit(0));
        menu.add(quit);
        return menu;
    }

    private JMenu generateWebUIMenu() {
        JMenu webUIMenu = new JMenu(tlUI(Lang.GUI_MENU_WEBUI));
        JMenuItem openWebUIMenuItem = new JMenuItem(tlUI(Lang.GUI_MENU_WEBUI_OPEN));
        openWebUIMenuItem.addActionListener(e -> {
            this.openWebUI();
        });
        webUIMenu.add(openWebUIMenuItem);
        JMenuItem copyWebUIToken = new JMenuItem(tlUI(Lang.GUI_COPY_WEBUI_TOKEN));
        copyWebUIToken.addActionListener(e -> {
            if (Main.getServer() != null && Main.getServer().getWebContainer() != null) {
                String content = Main.getServer().getWebContainer().getToken();
                copyText(content);
                swingGUI.createDialog(Level.INFO, tlUI(Lang.GUI_COPY_TO_CLIPBOARD_TITLE), String.format(tlUI(Lang.GUI_COPY_TO_CLIPBOARD_DESCRIPTION, content)));
            }
        });
        webUIMenu.add(copyWebUIToken);
        return webUIMenu;
    }

    private void openWebUI() {
        if (Main.getServer() != null && Main.getServer().getWebContainer() != null) {
            swingGUI.openWebpage(URI.create("http://localhost:" + Main.getServer().getWebContainer().javalin().port() + "?token=" + Main.getServer().getWebContainer().getToken()));
        }
    }

    public void sync() {

    }

    private void setupTabbedPane() {
        setTabTitle(tabbedPaneLogs, tlUI(Lang.GUI_TABBED_LOGS));
    }

    @Override
    public void dispose() {
        Main.getEventBus().unregister(this);
        super.dispose();
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /** Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(0, 0));
        tabbedPane = new JTabbedPane();
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        tabbedPaneLogs = new JPanel();
        tabbedPaneLogs.setLayout(new BorderLayout(0, 0));
        tabbedPane.addTab("Logs", tabbedPaneLogs);
        loggerScrollPane = new JScrollPane();
        Font loggerScrollPaneFont = this.$$$getFont$$$("Consolas", -1, -1, loggerScrollPane.getFont());
        if (loggerScrollPaneFont != null) loggerScrollPane.setFont(loggerScrollPaneFont);
        loggerScrollPane.setVerticalScrollBarPolicy(22);
        tabbedPaneLogs.add(loggerScrollPane, BorderLayout.CENTER);
        loggerTextList = new JList();
        loggerScrollPane.setViewportView(loggerTextList);
    }

    /** @noinspection ALL */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
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

    /** @noinspection ALL */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }


    private void createUIComponents() {
        // TODO: place custom component creation code here
    }


    public static class LogEntryRenderer extends JTextArea implements ListCellRenderer<LogEntry> {
        public LogEntryRenderer() {
            setLineWrap(true);       // 启用自动换行
            setWrapStyleWord(true);  // 换行时按单词
            setOpaque(true);         // 设置为不透明
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends LogEntry> list, LogEntry value, int index, boolean isSelected, boolean cellHasFocus) {
            setText(value.content().trim()); // 设置单元格内容

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
                switch (value.level()) {
                    case ERROR -> {
                        setBackground(new Color(255, 204, 187));
                        setForeground(Color.BLACK);
                    }
                    case WARN -> {
                        setBackground(new Color(255, 238, 204));
                        setForeground(Color.BLACK);
                    }
                }
            }
            setFont(list.getFont());

            // 动态计算行高，无需设置固定行高
            setSize(list.getWidth(), (int) getPreferredSize().getHeight());  // 设置 JTextArea 的宽度来支持换行
            return this;
        }
    }

}