package com.ghostchu.peerbanhelper.gui.impl.swing;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.LivePeersUpdatedEvent;
import com.ghostchu.peerbanhelper.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.ghostchu.peerbanhelper.wrapper.BakedPeerMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.wrapper.PeerMetadata;
import com.google.common.eventbus.Subscribe;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.net.URI;
import java.util.List;
import java.util.*;

@Slf4j
public class MainWindow extends JFrame {
    private final SwingGuiImpl swingGUI;
    private JPanel mainPanel;
    @Getter
    private JTextArea loggerTextArea;
    private JTabbedPane tabbedPane;
    //  private JPanel tabbedPaneWebUI;
    private JPanel tabbedPaneLogs;
    private JTable livePeers;
    private JPanel tabbedPaneLivePeers;
    private JButton resizeTable;
    @Nullable
    @Getter
    private TrayIcon trayIcon;
    private String[] peersTableColumn = new String[]{"Loading..."};
    private String[][] peersTableData = new String[0][0];

    public MainWindow(SwingGuiImpl swingGUI) {
        this.swingGUI = swingGUI;
        setJMenuBar(setupMenuBar());
        setTitle(String.format(Lang.GUI_TITLE_LOADED, "Swing UI", Main.getMeta().getVersion(), Main.getMeta().getAbbrev()));
        setSize(1000, 600);
        setContentPane(mainPanel);
        setupTabbedPane();
        setupSystemTray();
        setComponents();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowListener() {
            @Override
            public void windowOpened(WindowEvent e) {

            }

            @Override
            public void windowClosing(WindowEvent e) {
                minimizeToTray();
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
        Main.getEventBus().register(this);
        setVisible(!swingGUI.isSilentStart());
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

    private void minimizeToTray() {
        if (trayIcon != null) {
            setVisible(false);
            trayIcon.displayMessage(Lang.GUI_TRAY_MESSAGE_CAPTION, Lang.GUI_TRAY_MESSAGE_DESCRIPTION, TrayIcon.MessageType.INFO);
        }
    }

    private void setComponents() {
        setLivePeersTable();
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
        viewOnGithub.addActionListener(e -> swingGUI.openWebpage(URI.create(Lang.GITHUB_PAGE)));
        aboutMenu.add(viewOnGithub);
        return aboutMenu;
    }

    private JMenu generateWebUIMenu() {
        JMenu webUIMenu = new JMenu(Lang.GUI_MENU_WEBUI);
        JMenuItem openWebUIMenuItem = new JMenuItem(Lang.GUI_MENU_WEBUI_OPEN);

        openWebUIMenuItem.addActionListener(e -> {
            if (Main.getServer() != null && Main.getServer().getWebContainer() != null) {
                swingGUI.openWebpage(URI.create("http://localhost:" + Main.getServer().getWebContainer().javalin().port()));
            }
        });
        webUIMenu.add(openWebUIMenuItem);
        return webUIMenu;
    }

    public void sync() {

    }

    private void setupTabbedPane() {
        setTabTitle(tabbedPaneLogs, Lang.GUI_TABBED_LOGS);
        setTabTitle(tabbedPaneLivePeers, Lang.GUI_TABBED_PEERS);
    }

    private void setLivePeersTable() {
        livePeers.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        resizeTable.setText(Lang.GUI_BUTTON_RESIZE_TABLE);
        resizeTable.addActionListener(l -> fitTableColumns(livePeers));
        livePeers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        peersTableColumn = Lang.GUI_LIVE_PEERS_COLUMN_NAMES;
        livePeers.setModel(new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return peersTableData.length;
            }

            @Override
            public int getColumnCount() {
                return peersTableColumn.length;
            }

            @Override
            public String getColumnName(int columnIndex) {
                return peersTableColumn[columnIndex];
            }

            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                return peersTableData[rowIndex][columnIndex];
            }
        });
    }

    private void fitTableColumns(JTable myTable) {
        JTableHeader header = myTable.getTableHeader();
        int rowCount = myTable.getRowCount();
        Enumeration<TableColumn> columns = myTable.getColumnModel().getColumns();
        while (columns.hasMoreElements()) {
            TableColumn column = columns.nextElement();
            int col = header.getColumnModel().getColumnIndex(column.getIdentifier());
            int width = (int) myTable.getTableHeader().getDefaultRenderer()
                    .getTableCellRendererComponent(myTable, column.getIdentifier()
                            , false, false, -1, col).getPreferredSize().getWidth();
            for (int row = 0; row < rowCount; row++) {
                int preferedWidth = (int) myTable.getCellRenderer(row, col).getTableCellRendererComponent(myTable,
                        myTable.getValueAt(row, col), false, false, row, col).getPreferredSize().getWidth();
                width = Math.max(width, preferedWidth);
            }
            header.setResizingColumn(column);
            column.setWidth(width + myTable.getIntercellSpacing().width);
        }
    }

    private void updateLivePeersTable(String[][] data) {
        this.peersTableData = data;
        if (livePeers.isShowing()) { // 只在显示时重绘以显示数据更新，节约非 peers 页面的资源消耗
            livePeers.repaint();
        }
    }

    @Subscribe
    public void onLivePeersUpdated(LivePeersUpdatedEvent event) {
        String[][] data = new String[event.getLivePeers().size()][Lang.GUI_LIVE_PEERS_COLUMN_NAMES.length];
        List<Map.Entry<PeerAddress, PeerMetadata>> entrySet = new ArrayList<>(event.getLivePeers().entrySet());
        for (int i = 0; i < entrySet.size(); i++) {
            Map.Entry<PeerAddress, PeerMetadata> entry = entrySet.get(i);
            String countryRegion = "N/A";
            String ip = entry.getKey().getIp();
            String peerId = entry.getValue().getPeer().getId();
            String clientName = entry.getValue().getPeer().getClientName();
            String progress = String.format("%.1f", entry.getValue().getPeer().getProgress() * 100) + "%";
            String uploadSpeed = MsgUtil.humanReadableByteCountBin(entry.getValue().getPeer().getUploadSpeed()) + "/s";
            String uploaded = MsgUtil.humanReadableByteCountBin(entry.getValue().getPeer().getUploaded());
            String downloadSpeed = MsgUtil.humanReadableByteCountBin(entry.getValue().getPeer().getDownloadSpeed()) + "/s";
            String downloaded = MsgUtil.humanReadableByteCountBin(entry.getValue().getPeer().getDownloaded());
            String torrent = entry.getValue().getTorrent().getName();
            String city = "N/A";
            String asn = "N/A";
            String asOrg = "N/A";
            String asNetwork = "N/A";
            String isp = "N/A";
            String netType = "N/A";
            BakedPeerMetadata bakedBanMetadata = new BakedPeerMetadata(entry.getValue());
            IPGeoData ipGeoData = bakedBanMetadata.getGeo();
            if (ipGeoData != null) {
                if (ipGeoData.getCountry() != null) {
                    countryRegion = ipGeoData.getCountry().getIso();
                }
                if (ipGeoData.getCity() != null) {
                    city = ipGeoData.getCity().getName();
                }
                if (ipGeoData.getAs() != null) {
                    asn = "AS" + ipGeoData.getAs().getNumber();
                    asOrg = ipGeoData.getAs().getOrganization();
                    if (ipGeoData.getAs().getNetwork() != null) {
                        asNetwork = ipGeoData.getAs().getNetwork().getIpAddress();
                    }
                }
                if (ipGeoData.getNetwork() != null) {
                    isp = ipGeoData.getNetwork().getIsp();
                    netType = ipGeoData.getNetwork().getNetType();
                }
            }

            List<String> array = new ArrayList<>(); // 这里用 List，这样动态创建 array 就不用指定位置了
            array.add(countryRegion);
            array.add(ip);
            array.add(peerId);
            array.add(clientName);
            array.add(progress);
            array.add(uploadSpeed);
            array.add(uploaded);
            array.add(downloadSpeed);
            array.add(downloaded);
            array.add(torrent);
            array.add(city);
            array.add(asn);
            array.add(asOrg);
            array.add(asNetwork);
            array.add(isp);
            array.add(netType);
            System.arraycopy(array.toArray(new String[0]), 0, data[i], 0, array.size());
        }
        updateLivePeersTable(data);
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
        tabbedPaneLivePeers = new JPanel();
        tabbedPaneLivePeers.setLayout(new BorderLayout(0, 0));
        tabbedPane.addTab("Peers", tabbedPaneLivePeers);
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new BorderLayout(0, 0));
        tabbedPaneLivePeers.add(panel1, BorderLayout.NORTH);
        resizeTable = new JButton();
        resizeTable.setText("Resize");
        panel1.add(resizeTable, BorderLayout.WEST);
        final JScrollPane scrollPane2 = new JScrollPane();
        tabbedPaneLivePeers.add(scrollPane2, BorderLayout.CENTER);
        livePeers = new JTable();
        scrollPane2.setViewportView(livePeers);
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


    private void createUIComponents() {
        // TODO: place custom component creation code here
    }
}