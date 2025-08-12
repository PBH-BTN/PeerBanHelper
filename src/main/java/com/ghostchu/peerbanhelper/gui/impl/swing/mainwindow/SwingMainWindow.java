package com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow;

import com.formdev.flatlaf.util.SystemInfo;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.WebServerStartedEvent;
import com.ghostchu.peerbanhelper.gui.PBHGuiBridge;
import com.ghostchu.peerbanhelper.gui.impl.swing.SwingGuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component.*;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.google.common.eventbus.Subscribe;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Slf4j
public final class SwingMainWindow extends JFrame {
    @Getter
    private final SwingGuiImpl swingGUI;
    @Getter
    private final WindowMenuBar windowMenuBar;
    @Getter
    private final TrayMenu trayMenu;
    private JPanel mainPanel;
    private JTabbedPane tabbedPane;

    private PBHGuiBridge bridge;
    private final List<WindowTab> tabs = Collections.synchronizedList(new ArrayList<>());


    public SwingMainWindow(SwingGuiImpl swingGUI) {
        this.swingGUI = swingGUI;
        if (SystemInfo.isMacFullWindowContentSupported)
            getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        new WindowTitle(this);
        // max dimension size or 720p
        var maxAllowedWidth = Math.min(1280, Toolkit.getDefaultToolkit().getScreenSize().width);
        var maxAllowedHeight = Math.min(720, Toolkit.getDefaultToolkit().getScreenSize().height);
        setSize(maxAllowedWidth, maxAllowedHeight);
        setContentPane(mainPanel);
        this.windowMenuBar = new WindowMenuBar(this);
        this.trayMenu = new TrayMenu(this);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                trayMenu.minimizeToTray();
            }
        });
        ImageIcon imageIcon = new ImageIcon(Main.class.getResource("/assets/icon.png"));
        setIconImage(imageIcon.getImage());
        setVisible(!swingGUI.isSilentStart());
        if (SwingUtilities.isEventDispatchThread()) {
            registerTabs();
        } else {
            try {
                SwingUtilities.invokeAndWait(this::registerTabs);
            } catch (InterruptedException | InvocationTargetException e) {
                log.debug("Unable to register Tabs", e);
            }
        }
        //this.webuiTab = new WebUITab(this);
        Main.getEventBus().register(this);

    }

    private void registerTabs() {
        tabs.add(new LogsTab(this));
        if (MiscUtil.isClassAvailable("org.eclipse.swt.SWT")) {
            try { // SWT possible be null here on unsupported platform
                tabs.add(new WebUITab(this));
            } catch (Exception e) {
                log.error("Unable to create WebUITab or PerfProfilerTab", e);
            }
        } else {
            log.debug("SWT is not available, WebUITab and PerfProfilerTab will not be created.");
        }
        tabs.forEach(WindowTab::onWindowShow);
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);
        if (b) {
            tabs.forEach(WindowTab::onWindowShow);
        } else {
            tabs.forEach(WindowTab::onWindowHide);
        }
    }

    @Subscribe
    public void onWebServerStarted(WebServerStartedEvent event) {
        this.bridge = Main.getApplicationContext().getBean(PBHGuiBridge.class);
        tabs.forEach(tab -> tab.onStarted(bridge));
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


    public void sync() {
    }


    public void openWebUI() {
        bridge.getWebUiUrl().ifPresent(swingGUI::openWebpage);
    }


    @Override
    public void dispose() {
        Main.getEventBus().unregister(this);
        //this.webuiTab.close();
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

    }


    /** @noinspection ALL */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

    /** @noinspection ALL */
    public Font getFont(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1') && testFont.canDisplay('中')) {
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

    public <T extends WindowTab> T getTab(Class<T> tabClass) {
        for (WindowTab tab : tabs) {
            if (tab.getClass().equals(tabClass)) {
                //noinspection unchecked
                return (T) tab;
            }
        }
        throw new IllegalStateException("Tab not found: " + tabClass.getName());
    }


    private void createUIComponents() {
        // TODO: place custom component creation code here
    }

    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }
}