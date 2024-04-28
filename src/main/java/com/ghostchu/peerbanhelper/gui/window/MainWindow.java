package com.ghostchu.peerbanhelper.gui.window;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.gui.impl.swing.SwingGuiImpl;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.net.URI;
import java.util.Locale;

@Slf4j
public class MainWindow extends JFrame {
    private final SwingGuiImpl swingGUI;
    private JPanel mainPanel;
    @Getter
    private JTextArea loggerTextArea;
    private JTabbedPane tabbedPane;
    private JPanel tabbedPaneWebUI;
    private JPanel tabbedPaneLogs;
    @Nullable
    private TrayIcon trayIcon;

    public MainWindow(SwingGuiImpl swingGUI) {
        this.swingGUI = swingGUI;
        setJMenuBar(setupMenuBar());
        //sendCommandBtn.addActionListener(e -> guiManager.setColorTheme(FlatDarculaLaf.class));
        setTitle("PeerBanHelper (GUI) - v" + Main.getMeta().getVersion() + "(" + Main.getMeta().getAbbrev() + ")");
        setVisible(true);
        setSize(1000, 600);
        setContentPane(mainPanel);
        setupTabbedPane();
        setupSystemTray();
        //setupLoggerForward();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowListener() {
            @Override
            public void windowOpened(WindowEvent e) {

            }

            @Override
            public void windowClosing(WindowEvent e) {
                if (trayIcon != null) {
                    setVisible(false);
                    trayIcon.displayMessage(Lang.GUI_TRAY_MESSAGE_CAPTION, Lang.GUI_TRAY_MESSAGE_DESCRIPTION, TrayIcon.MessageType.INFO);
                }
            }

            @Override
            public void windowClosed(WindowEvent e) {

            }

            @Override
            public void windowIconified(WindowEvent e) {

            }

            @Override
            public void windowDeiconified(WindowEvent e) {

            }

            @Override
            public void windowActivated(WindowEvent e) {

            }

            @Override
            public void windowDeactivated(WindowEvent e) {

            }
        });
        ImageIcon imageIcon = new ImageIcon(Main.class.getResource("/assets/icon.png"));
        setIconImage(imageIcon.getImage());
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

    private void setupTabbedPane() {
        setTabTitle(tabbedPaneLogs, Lang.GUI_TABBED_LOGS);
    }

    private void setupSystemTray() {
        if (SystemTray.isSupported()) {
            TrayIcon icon = new TrayIcon(Toolkit.getDefaultToolkit().getImage(Main.class.getResource("/assets/icon.png")));
            icon.setImageAutoSize(true);
            //创建弹出菜单
            PopupMenu menu = new PopupMenu();
            //添加一个用于退出的按钮
            MenuItem item = new MenuItem("Exit");
            item.addActionListener(e -> System.exit(0));
            menu.add(item);
            //添加弹出菜单到托盘图标
            icon.setPopupMenu(menu);
            SystemTray tray = SystemTray.getSystemTray();//获取系统托盘
            icon.addActionListener(e -> setVisible(true));
            try {
                tray.add(icon);//将托盘图表添加到系统托盘
                this.trayIcon = icon;
            } catch (AWTException e) {
                throw new RuntimeException(e);
            }

        }
    }

    private JMenuBar setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(generateWebUIMenu());
        menuBar.add(generateAboutMenu());
        this.add(menuBar, BorderLayout.NORTH);
        return menuBar;
    }

    private Component generateAboutMenu() {
        JMenu aboutMenu = new JMenu(Lang.GUI_MENU_ABOUT);
        JMenuItem viewOnGithub = new JMenuItem(Lang.ABOUT_VIEW_GITHUB);
        viewOnGithub.addActionListener(e -> swingGUI.openWebpage(URI.create("https://github.com/PBH-BTN/PeerBanHelper")));
        aboutMenu.add(viewOnGithub);
        return aboutMenu;
    }

    private JMenu generateWebUIMenu() {
        JMenu webUIMenu = new JMenu(Lang.GUI_MENU_WEBUI);
        JMenuItem openWebUIMenuItem = new JMenuItem(Lang.GUI_MENU_WEBUI_OPEN);
        openWebUIMenuItem.addActionListener(e -> swingGUI.openWebpage(URI.create("http://127.0.0.1:" + Main.getServer().getWebManagerServer().getListeningPort())));
        webUIMenu.add(openWebUIMenuItem);
        return webUIMenu;
    }

    public void sync() {

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

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
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
        final JScrollPane scrollPane1 = new JScrollPane();
        Font scrollPane1Font = this.$$$getFont$$$("Consolas", -1, -1, scrollPane1.getFont());
        if (scrollPane1Font != null) scrollPane1.setFont(scrollPane1Font);
        scrollPane1.setVerticalScrollBarPolicy(22);
        tabbedPaneLogs.add(scrollPane1, BorderLayout.CENTER);
        loggerTextArea = new JTextArea();
        loggerTextArea.setEditable(false);
        loggerTextArea.setLineWrap(true);
        loggerTextArea.setWrapStyleWord(true);
        scrollPane1.setViewportView(loggerTextArea);
    }

    /**
     * @noinspection ALL
     */
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

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }
}