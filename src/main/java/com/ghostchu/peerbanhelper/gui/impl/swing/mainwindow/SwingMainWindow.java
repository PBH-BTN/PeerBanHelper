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
        
        // Initialize GUI tabs with registered components
        initializeGuiTabs();
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
     * Initialize the GUI tabs with WebUI-equivalent functionality
     */
    private void initializeGuiTabs() {
        SwingUtilities.invokeLater(() -> {
            // Logs tab is already the first tab from the form designer
            // Add additional tabs that mirror WebUI functionality
            addDashboardTab();
            addBanListTab();
            addDataTabs();
            addRuleManagementTabs();
            addMetricsTabs();
            addSettingsTab();
            
            // Ensure logs tab remains selected as the default
            tabbedPane.setSelectedIndex(0);
        });
    }
    
    /**
     * Add dashboard tab (similar to WebUI dashboard)
     */
    private void addDashboardTab() {
        dashboardPanel = new JPanel();
        dashboardPanel.setLayout(new BoxLayout(dashboardPanel, BoxLayout.Y_AXIS));
        
        // Get enabled components and add them to the dashboard
        var enabledComponents = componentRegistry.getEnabledComponents();
        updateDashboardComponents(enabledComponents);
        
        JScrollPane dashboardScrollPane = new JScrollPane(dashboardPanel);
        dashboardScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        dashboardScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        tabbedPane.addTab("仪表板", null, dashboardScrollPane, "显示基本统计信息和程序运行状态");
    }
    
    /**
     * Add ban list tab
     */
    private void addBanListTab() {
        JPanel banListPanel = new JPanel(new BorderLayout());
        JLabel banListLabel = new JLabel("封禁列表功能 - 待实现");
        banListLabel.setHorizontalAlignment(SwingConstants.CENTER);
        banListPanel.add(banListLabel, BorderLayout.CENTER);
        
        tabbedPane.addTab("封禁列表", null, banListPanel, "查看和管理封禁列表");
    }
    
    /**
     * Add data-related tabs 
     */
    private void addDataTabs() {
        // For now, create a simple tabbed pane for data sub-tabs
        JTabbedPane dataTabPane = new JTabbedPane();
        
        // Ban logs tab
        JPanel banLogsPanel = new JPanel(new BorderLayout());
        JLabel banLogsLabel = new JLabel("封禁日志 - 待实现");
        banLogsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        banLogsPanel.add(banLogsLabel, BorderLayout.CENTER);
        dataTabPane.addTab("封禁日志", banLogsPanel);
        
        // Torrent history tab  
        JPanel torrentPanel = new JPanel(new BorderLayout());
        JLabel torrentLabel = new JLabel("种子历史 - 待实现");
        torrentLabel.setHorizontalAlignment(SwingConstants.CENTER);
        torrentPanel.add(torrentLabel, BorderLayout.CENTER);
        dataTabPane.addTab("种子历史", torrentPanel);
        
        // IP history tab
        JPanel ipHistoryPanel = new JPanel(new BorderLayout());
        JLabel ipHistoryLabel = new JLabel("IP历史 - 待实现");
        ipHistoryLabel.setHorizontalAlignment(SwingConstants.CENTER);
        ipHistoryPanel.add(ipHistoryLabel, BorderLayout.CENTER);
        dataTabPane.addTab("IP历史", ipHistoryPanel);
        
        tabbedPane.addTab("数据", null, dataTabPane, "查看封禁日志、种子历史等数据");
    }
    
    /**
     * Add rule management tabs
     */
    private void addRuleManagementTabs() {
        JTabbedPane ruleTabPane = new JTabbedPane();
        
        // Rule subscription tab
        JPanel ruleSubPanel = new JPanel(new BorderLayout());
        JLabel ruleSubLabel = new JLabel("规则订阅 - 待实现");
        ruleSubLabel.setHorizontalAlignment(SwingConstants.CENTER);
        ruleSubPanel.add(ruleSubLabel, BorderLayout.CENTER);
        ruleTabPane.addTab("规则订阅", ruleSubPanel);
        
        // Custom scripts tab
        JPanel scriptsPanel = new JPanel(new BorderLayout());
        JLabel scriptsLabel = new JLabel("自定义脚本 - 待实现");
        scriptsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        scriptsPanel.add(scriptsLabel, BorderLayout.CENTER);
        ruleTabPane.addTab("自定义脚本", scriptsPanel);
        
        // IP rules tab
        JPanel ipRulesPanel = new JPanel(new BorderLayout());
        JLabel ipRulesLabel = new JLabel("IP规则 - 待实现");
        ipRulesLabel.setHorizontalAlignment(SwingConstants.CENTER);
        ipRulesPanel.add(ipRulesLabel, BorderLayout.CENTER);
        ruleTabPane.addTab("IP规则", ipRulesPanel);
        
        // Port rules tab
        JPanel portRulesPanel = new JPanel(new BorderLayout());
        JLabel portRulesLabel = new JLabel("端口规则 - 待实现");
        portRulesLabel.setHorizontalAlignment(SwingConstants.CENTER);
        portRulesPanel.add(portRulesLabel, BorderLayout.CENTER);
        ruleTabPane.addTab("端口规则", portRulesPanel);
        
        // ASN rules tab
        JPanel asnRulesPanel = new JPanel(new BorderLayout());
        JLabel asnRulesLabel = new JLabel("ASN规则 - 待实现");
        asnRulesLabel.setHorizontalAlignment(SwingConstants.CENTER);
        asnRulesPanel.add(asnRulesLabel, BorderLayout.CENTER);
        ruleTabPane.addTab("ASN规则", asnRulesPanel);
        
        // Region rules tab
        JPanel regionRulesPanel = new JPanel(new BorderLayout());
        JLabel regionRulesLabel = new JLabel("地区规则 - 待实现");
        regionRulesLabel.setHorizontalAlignment(SwingConstants.CENTER);
        regionRulesPanel.add(regionRulesLabel, BorderLayout.CENTER);
        ruleTabPane.addTab("地区规则", regionRulesPanel);
        
        // City rules tab
        JPanel cityRulesPanel = new JPanel(new BorderLayout());
        JLabel cityRulesLabel = new JLabel("城市规则 - 待实现");
        cityRulesLabel.setHorizontalAlignment(SwingConstants.CENTER);
        cityRulesPanel.add(cityRulesLabel, BorderLayout.CENTER);
        ruleTabPane.addTab("城市规则", cityRulesPanel);
        
        tabbedPane.addTab("规则管理", null, ruleTabPane, "管理各种封禁规则");
    }
    
    /**
     * Add metrics tabs
     */
    private void addMetricsTabs() {
        JTabbedPane metricsTabPane = new JTabbedPane();
        
        // Charts tab
        JPanel chartsPanel = new JPanel(new BorderLayout());
        JLabel chartsLabel = new JLabel("统计图表 - 待实现");
        chartsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        chartsPanel.add(chartsLabel, BorderLayout.CENTER);
        metricsTabPane.addTab("统计图表", chartsPanel);
        
        // Rankings tab
        JPanel rankingsPanel = new JPanel(new BorderLayout());
        JLabel rankingsLabel = new JLabel("排行榜 - 待实现");
        rankingsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        rankingsPanel.add(rankingsLabel, BorderLayout.CENTER);
        metricsTabPane.addTab("排行榜", rankingsPanel);
        
        tabbedPane.addTab("统计", null, metricsTabPane, "查看统计图表和排行榜");
    }
    
    /**
     * Add settings tab
     */
    private void addSettingsTab() {
        JPanel settingsPanel = new JPanel(new BorderLayout());
        JLabel settingsLabel = new JLabel("设置配置 - 待实现");
        settingsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        settingsPanel.add(settingsLabel, BorderLayout.CENTER);
        
        tabbedPane.addTab("设置", null, settingsPanel, "程序设置和配置");
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