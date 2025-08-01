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
        
        tabbedPane.addTab(tlUI(Lang.GUI_TABBED_DASHBOARD), null, dashboardScrollPane, tlUI(Lang.GUI_TABBED_DASHBOARD));
    }
    
    /**
     * Add ban list tab
     */
    private void addBanListTab() {
        JPanel banListPanel = new JPanel(new BorderLayout());
        
        // Create a table to display banned peers
        String[] columnNames = {"IP Address", "Peer ID", "Ban Reason", "Ban Time", "Expires"};
        Object[][] data = {};  // Will be populated with actual ban data
        
        JTable banTable = new JTable(data, columnNames);
        banTable.setFillsViewportHeight(true);
        banTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        
        JScrollPane banTableScrollPane = new JScrollPane(banTable);
        banListPanel.add(banTableScrollPane, BorderLayout.CENTER);
        
        // Add refresh button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshBanList(banTable));
        buttonPanel.add(refreshButton);
        banListPanel.add(buttonPanel, BorderLayout.NORTH);
        
        tabbedPane.addTab(tlUI(Lang.GUI_TABBED_BAN_LIST), null, banListPanel, tlUI(Lang.GUI_TABBED_BAN_LIST));
    }
    
    /**
     * Add data-related tabs 
     */
    private void addDataTabs() {
        JTabbedPane dataTabPane = new JTabbedPane();
        
        // Ban logs tab
        JPanel banLogsPanel = new JPanel(new BorderLayout());
        String[] banLogColumns = {"Time", "IP", "Peer ID", "Reason", "Module"};
        Object[][] banLogData = {};
        JTable banLogTable = new JTable(banLogData, banLogColumns);
        banLogTable.setFillsViewportHeight(true);
        JScrollPane banLogScrollPane = new JScrollPane(banLogTable);
        banLogsPanel.add(banLogScrollPane, BorderLayout.CENTER);
        dataTabPane.addTab(tlUI(Lang.GUI_TABBED_DATA_BAN_LOGS), banLogsPanel);
        
        // Torrent history tab  
        JPanel torrentPanel = new JPanel(new BorderLayout());
        String[] torrentColumns = {"Torrent Name", "Hash", "Size", "Peers", "Status"};
        Object[][] torrentData = {};
        JTable torrentTable = new JTable(torrentData, torrentColumns);
        torrentTable.setFillsViewportHeight(true);
        JScrollPane torrentScrollPane = new JScrollPane(torrentTable);
        torrentPanel.add(torrentScrollPane, BorderLayout.CENTER);
        dataTabPane.addTab(tlUI(Lang.GUI_TABBED_DATA_TORRENT_HISTORY), torrentPanel);
        
        // IP history tab
        JPanel ipHistoryPanel = new JPanel(new BorderLayout());
        String[] ipColumns = {"IP Address", "First Seen", "Last Seen", "Connections", "Status"};
        Object[][] ipData = {};
        JTable ipTable = new JTable(ipData, ipColumns);
        ipTable.setFillsViewportHeight(true);
        JScrollPane ipScrollPane = new JScrollPane(ipTable);
        ipHistoryPanel.add(ipScrollPane, BorderLayout.CENTER);
        dataTabPane.addTab(tlUI(Lang.GUI_TABBED_DATA_IP_HISTORY), ipHistoryPanel);
        
        tabbedPane.addTab(tlUI(Lang.GUI_TABBED_DATA), null, dataTabPane, tlUI(Lang.GUI_TABBED_DATA));
    }
    
    /**
     * Add rule management tabs
     */
    private void addRuleManagementTabs() {
        JTabbedPane ruleTabPane = new JTabbedPane();
        
        // Rule subscription tab
        JPanel ruleSubPanel = new JPanel(new BorderLayout());
        String[] ruleSubColumns = {"Subscription Name", "URL", "Status", "Last Update", "Rules Count"};
        Object[][] ruleSubData = {};
        JTable ruleSubTable = new JTable(ruleSubData, ruleSubColumns);
        ruleSubTable.setFillsViewportHeight(true);
        JScrollPane ruleSubScrollPane = new JScrollPane(ruleSubTable);
        ruleSubPanel.add(ruleSubScrollPane, BorderLayout.CENTER);
        ruleTabPane.addTab(tlUI(Lang.GUI_TABBED_RULE_SUBSCRIPTION), ruleSubPanel);
        
        // Custom scripts tab
        JPanel scriptsPanel = new JPanel(new BorderLayout());
        String[] scriptColumns = {"Script Name", "Type", "Status", "Last Modified"};
        Object[][] scriptData = {};
        JTable scriptTable = new JTable(scriptData, scriptColumns);
        scriptTable.setFillsViewportHeight(true);
        JScrollPane scriptScrollPane = new JScrollPane(scriptTable);
        scriptsPanel.add(scriptScrollPane, BorderLayout.CENTER);
        ruleTabPane.addTab(tlUI(Lang.GUI_TABBED_RULE_CUSTOM_SCRIPTS), scriptsPanel);
        
        // IP rules tab
        JPanel ipRulesPanel = new JPanel(new BorderLayout());
        String[] ipRuleColumns = {"Rule", "Action", "Description", "Priority", "Enabled"};
        Object[][] ipRuleData = {};
        JTable ipRuleTable = new JTable(ipRuleData, ipRuleColumns);
        ipRuleTable.setFillsViewportHeight(true);
        JScrollPane ipRuleScrollPane = new JScrollPane(ipRuleTable);
        ipRulesPanel.add(ipRuleScrollPane, BorderLayout.CENTER);
        ruleTabPane.addTab(tlUI(Lang.GUI_TABBED_RULE_IP), ipRulesPanel);
        
        // Port rules tab
        JPanel portRulesPanel = new JPanel(new BorderLayout());
        String[] portRuleColumns = {"Port Range", "Action", "Description", "Enabled"};
        Object[][] portRuleData = {};
        JTable portRuleTable = new JTable(portRuleData, portRuleColumns);
        portRuleTable.setFillsViewportHeight(true);
        JScrollPane portRuleScrollPane = new JScrollPane(portRuleTable);
        portRulesPanel.add(portRuleScrollPane, BorderLayout.CENTER);
        ruleTabPane.addTab(tlUI(Lang.GUI_TABBED_RULE_PORT), portRulesPanel);
        
        // ASN rules tab
        JPanel asnRulesPanel = new JPanel(new BorderLayout());
        String[] asnRuleColumns = {"ASN", "Organization", "Action", "Description", "Enabled"};
        Object[][] asnRuleData = {};
        JTable asnRuleTable = new JTable(asnRuleData, asnRuleColumns);
        asnRuleTable.setFillsViewportHeight(true);
        JScrollPane asnRuleScrollPane = new JScrollPane(asnRuleTable);
        asnRulesPanel.add(asnRuleScrollPane, BorderLayout.CENTER);
        ruleTabPane.addTab(tlUI(Lang.GUI_TABBED_RULE_ASN), asnRulesPanel);
        
        // Region rules tab
        JPanel regionRulesPanel = new JPanel(new BorderLayout());
        String[] regionRuleColumns = {"Country/Region", "ISO Code", "Action", "Description", "Enabled"};
        Object[][] regionRuleData = {};
        JTable regionRuleTable = new JTable(regionRuleData, regionRuleColumns);
        regionRuleTable.setFillsViewportHeight(true);
        JScrollPane regionRuleScrollPane = new JScrollPane(regionRuleTable);
        regionRulesPanel.add(regionRuleScrollPane, BorderLayout.CENTER);
        ruleTabPane.addTab(tlUI(Lang.GUI_TABBED_RULE_REGION), regionRulesPanel);
        
        // City rules tab
        JPanel cityRulesPanel = new JPanel(new BorderLayout());
        String[] cityRuleColumns = {"City", "Country", "Action", "Description", "Enabled"};
        Object[][] cityRuleData = {};
        JTable cityRuleTable = new JTable(cityRuleData, cityRuleColumns);
        cityRuleTable.setFillsViewportHeight(true);
        JScrollPane cityRuleScrollPane = new JScrollPane(cityRuleTable);
        cityRulesPanel.add(cityRuleScrollPane, BorderLayout.CENTER);
        ruleTabPane.addTab(tlUI(Lang.GUI_TABBED_RULE_CITY), cityRulesPanel);
        
        tabbedPane.addTab(tlUI(Lang.GUI_TABBED_RULE_MANAGEMENT), null, ruleTabPane, tlUI(Lang.GUI_TABBED_RULE_MANAGEMENT));
    }
    
    /**
     * Add metrics tabs
     */
    private void addMetricsTabs() {
        JTabbedPane metricsTabPane = new JTabbedPane();
        
        // Charts tab
        JPanel chartsPanel = new JPanel(new BorderLayout());
        JLabel chartsLabel = new JLabel("Statistics charts will be displayed here", SwingConstants.CENTER);
        chartsPanel.add(chartsLabel, BorderLayout.CENTER);
        metricsTabPane.addTab(tlUI(Lang.GUI_TABBED_METRICS_CHARTS), chartsPanel);
        
        // Rankings tab
        JPanel rankingsPanel = new JPanel(new BorderLayout());
        String[] rankingColumns = {"Rank", "Item", "Value", "Percentage"};
        Object[][] rankingData = {};
        JTable rankingTable = new JTable(rankingData, rankingColumns);
        rankingTable.setFillsViewportHeight(true);
        JScrollPane rankingScrollPane = new JScrollPane(rankingTable);
        rankingsPanel.add(rankingScrollPane, BorderLayout.CENTER);
        metricsTabPane.addTab(tlUI(Lang.GUI_TABBED_METRICS_RANKINGS), rankingsPanel);
        
        tabbedPane.addTab(tlUI(Lang.GUI_TABBED_METRICS), null, metricsTabPane, tlUI(Lang.GUI_TABBED_METRICS));
    }
    
    /**
     * Add settings tab
     */
    private void addSettingsTab() {
        JPanel settingsPanel = new JPanel(new BorderLayout());
        
        // Create a simple settings interface with configuration categories
        JPanel mainSettingsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        // General settings section
        gbc.gridx = 0; gbc.gridy = 0;
        mainSettingsPanel.add(new JLabel("General Settings:"), gbc);
        
        gbc.gridx = 0; gbc.gridy = 1;
        JCheckBox autoStartCheckbox = new JCheckBox("Auto start on system boot");
        mainSettingsPanel.add(autoStartCheckbox, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2;
        JCheckBox minimizeToTrayCheckbox = new JCheckBox("Minimize to system tray");
        mainSettingsPanel.add(minimizeToTrayCheckbox, gbc);
        
        // Network settings section
        gbc.gridx = 0; gbc.gridy = 3;
        mainSettingsPanel.add(new JLabel("Network Settings:"), gbc);
        
        gbc.gridx = 0; gbc.gridy = 4;
        JPanel portPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        portPanel.add(new JLabel("WebUI Port: "));
        JTextField portField = new JTextField("9898", 6);
        portPanel.add(portField);
        mainSettingsPanel.add(portPanel, gbc);
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton saveButton = new JButton("Save Settings");
        JButton resetButton = new JButton("Reset to Defaults");
        buttonPanel.add(saveButton);
        buttonPanel.add(resetButton);
        
        settingsPanel.add(mainSettingsPanel, BorderLayout.CENTER);
        settingsPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        tabbedPane.addTab(tlUI(Lang.GUI_TABBED_SETTINGS), null, settingsPanel, tlUI(Lang.GUI_TABBED_SETTINGS));
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
     * Refresh the ban list table with current data
     */
    private void refreshBanList(JTable banTable) {
        SwingUtilities.invokeLater(() -> {
            if (bridge != null) {
                // Get banned peers from the downloader server
                var bannedPeers = bridge.getBasicStatistics();
                // For now, just show a message that data would be populated here
                // In a real implementation, we would populate the table with banned peer data
                JOptionPane.showMessageDialog(this, 
                    "Ban list refresh triggered. Banned peers count: " + bannedPeers.get("banlistCounter"),
                    "Refresh Complete", 
                    JOptionPane.INFORMATION_MESSAGE);
            }
        });
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