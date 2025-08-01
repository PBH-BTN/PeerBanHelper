package com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow;

import com.formdev.flatlaf.util.SystemInfo;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.WebServerStartedEvent;
import com.ghostchu.peerbanhelper.gui.PBHGuiBridge;
import com.ghostchu.peerbanhelper.gui.component.GuiComponent;
import com.ghostchu.peerbanhelper.gui.component.GuiComponentRegistry;
import com.ghostchu.peerbanhelper.gui.impl.swing.SwingGuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component.LogsTab;
import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component.TrayMenu;
import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component.WindowMenuBar;
import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component.WindowTitle;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.logger.LogEntry;
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
import java.util.Locale;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class SwingMainWindow extends JFrame {
    @Getter
    private final SwingGuiImpl swingGUI;
    @Getter
    private final LogsTab logsTab;
    @Getter
    private final WindowMenuBar windowMenuBar;
    @Getter
    private final TrayMenu trayMenu;
    private JPanel mainPanel;
    private JTabbedPane tabbedPane;
    private JPanel tabbedPaneLogs;
    @Getter
    private JList<LogEntry> loggerTextList;
    @Getter
    private JScrollPane loggerScrollPane;
    private PBHGuiBridge bridge;
    private GuiComponentRegistry componentRegistry;
    private JPanel dashboardPanel;


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
        setupTabbedPane();
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
        this.logsTab = new LogsTab(this);
        //this.webuiTab = new WebUITab(this);
        Main.getEventBus().register(this);
    }

    @Subscribe
    public void onWebServerStarted(WebServerStartedEvent event) {
        this.bridge = Main.getApplicationContext().getBean(PBHGuiBridge.class);
        this.componentRegistry = Main.getApplicationContext().getBean(GuiComponentRegistry.class);
        
        // Initialize dashboard with registered components
        initializeDashboard();
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


    private void setupTabbedPane() {
        setTabTitle(tabbedPaneLogs, tlUI(Lang.GUI_TABBED_LOGS));
    }


    public void openWebUI() {
        bridge.getWebUiUrl().ifPresent(swingGUI::openWebpage);
    }
    
    /**
     * Initialize the dashboard with registered GUI components
     */
    private void initializeDashboard() {
        SwingUtilities.invokeLater(() -> {
            // Create dashboard panel if it doesn't exist
            if (dashboardPanel == null) {
                createDashboardPanel();
            }
            
            // Get enabled components and add them to the dashboard
            var enabledComponents = componentRegistry.getEnabledComponents();
            updateDashboardComponents(enabledComponents);
            
            // Start periodic updates
            startPeriodicUpdates();
        });
    }
    
    /**
     * Create the dashboard panel and add it as a tab
     */
    private void createDashboardPanel() {
        dashboardPanel = new JPanel();
        dashboardPanel.setLayout(new BoxLayout(dashboardPanel, BoxLayout.Y_AXIS));
        
        JScrollPane dashboardScrollPane = new JScrollPane(dashboardPanel);
        dashboardScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        dashboardScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        // Add dashboard tab as the first tab
        tabbedPane.insertTab("仪表板", null, dashboardScrollPane, "显示基本统计信息和程序运行状态", 0);
        tabbedPane.setSelectedIndex(0); // Select the dashboard tab by default
    }
    
    /**
     * Update dashboard with the provided components
     */
    private void updateDashboardComponents(java.util.List<GuiComponent> components) {
        dashboardPanel.removeAll();
        
        for (GuiComponent component : components) {
            JPanel componentPanel = component.getPanel();
            if (componentPanel != null) {
                // Add some spacing around each component
                componentPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(5, 5, 5, 5),
                    componentPanel.getBorder()
                ));
                dashboardPanel.add(componentPanel);
            }
        }
        
        // Add glue to push components to the top
        dashboardPanel.add(Box.createVerticalGlue());
        
        // Refresh the UI
        dashboardPanel.revalidate();
        dashboardPanel.repaint();
    }
    
    /**
     * Start periodic updates for all components
     */
    private void startPeriodicUpdates() {
        // Use a timer to update components every 5 seconds
        Timer timer = new Timer(5000, e -> {
            if (componentRegistry != null) {
                componentRegistry.updateAllComponents();
            }
        });
        timer.start();
        
        // Store timer reference for cleanup if needed
        // Note: In a real implementation, you might want to store this for proper cleanup
    }


    @Override
    public void dispose() {
        Main.getEventBus().unregister(this);
        if (componentRegistry != null) {
            componentRegistry.dispose();
        }
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
        tabbedPaneLogs = new JPanel();
        tabbedPaneLogs.setLayout(new BorderLayout(0, 0));
        tabbedPane.addTab("Logs", tabbedPaneLogs);
        loggerScrollPane = new JScrollPane();
        loggerScrollPane.setEnabled(true);
        Font loggerScrollPaneFont = this.$$$getFont$$$(null, -1, -1, loggerScrollPane.getFont());
        if (loggerScrollPaneFont != null) loggerScrollPane.setFont(loggerScrollPaneFont);
        loggerScrollPane.setVerticalScrollBarPolicy(22);
        tabbedPaneLogs.add(loggerScrollPane, BorderLayout.CENTER);
        loggerTextList = new JList();
        Font loggerTextListFont = UIManager.getFont("TextArea.font");
        if (loggerTextListFont != null) loggerTextList.setFont(loggerTextListFont);
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

    /** @noinspection ALL */
    protected Font getFont(String fontName, int style, int size, Font currentFont) {
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


    private void createUIComponents() {
        // TODO: place custom component creation code here
    }


}