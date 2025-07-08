//package com.ghostchu.peerbanhelper.gui.impl.qt;
//
//import com.ghostchu.peerbanhelper.ExternalSwitch;
//import com.ghostchu.peerbanhelper.Main;
//import com.ghostchu.peerbanhelper.gui.impl.qt.tabs.QtLogsTabComponent;
//import com.ghostchu.peerbanhelper.gui.impl.qt.tabs.QtTabComponent;
//import com.ghostchu.peerbanhelper.text.Lang;
//import io.qt.gui.QAction;
//import io.qt.gui.QCloseEvent;
//import io.qt.gui.QIcon;
//import io.qt.gui.QPixmap;
//import io.qt.widgets.*;
//import lombok.Getter;
//import lombok.extern.slf4j.Slf4j;
//import org.slf4j.event.Level;
//
//import java.awt.*;
//import java.awt.datatransfer.Clipboard;
//import java.awt.datatransfer.StringSelection;
//import java.io.IOException;
//import java.net.URI;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//
//import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;
//
//@Slf4j
//@Getter
//public class QtMainWindow extends QMainWindow {
//    private final QtGuiImpl qtGui;
//    private QTabWidget tabWidget;    // Tab 组件
//    private final List<QtTabComponent> tabComponents = new ArrayList<>();
//    private QtLogsTabComponent logsTabComponent;
//
//    // 图标资源
//    private QIcon iconImage;
//
//    // 托盘管理器
//    private final QtTrayManager trayManager;
//
//    public QtMainWindow(QtGuiImpl qtGui) {
//        super();
//        this.qtGui = qtGui;
//        setWindowTitle(tlUI(Lang.GUI_TITLE_LOADING));
//        resize(1280, 720);
//        loadIconImage();
//        if (iconImage != null) {
//            setWindowIcon(iconImage);
//        }
//        createMenuBar();
//        if (!qtGui.isSilentStart()) {
//            show();
//        }
//        createComponents();
//        trayManager = new QtTrayManager(this, iconImage, tabComponents);
//    }
//
//    // 加载图标资源
//    private void loadIconImage() {
//        try {
//            var iconStream = Main.class.getResourceAsStream("/assets/icon.png");
//            if (iconStream != null) {
//                QPixmap pixmap = new QPixmap();
//                if (pixmap.loadFromData(iconStream.readAllBytes())) {
//                    iconImage = new QIcon(pixmap);
//                }
//            }
//        } catch (Exception e) {
//            log.error("Unable to load icon image", e);
//        }
//    }
//
//    private void createComponents() {
//        // 创建中央控件
//        QWidget centralWidget = new QWidget();
//        setCentralWidget(centralWidget);
//
//        // 设置布局
//        QVBoxLayout layout = new QVBoxLayout(centralWidget);        // 创建标签页控件
//        this.tabWidget = new QTabWidget();
//        layout.addWidget(tabWidget);        // 创建并添加 Tab 组件
//        logsTabComponent = new QtLogsTabComponent();
//
//        tabComponents.add(logsTabComponent);
//
//        // 为每个组件创建 Tab
//        for (QtTabComponent component : tabComponents) {
//            component.createTab(tabWidget);
//        }
//    }    // 获取日志 Tab 组件
//
//    public QtLogsTabComponent getLogsTabComponent() {
//        return logsTabComponent;
//    }
//
//    // 获取托盘管理器
//    public QtTrayManager getTrayManager() {
//        return trayManager;
//    }
//
//    // 创建菜单栏
//    private void createMenuBar() {
//        QMenuBar menuBar = this.menuBar();
//
//        // 添加程序菜单
//        createProgramMenu(menuBar);
//
//        // 添加 WebUI 菜单
//        createWebUIMenu(menuBar);
//
//        createDebugMenu(menuBar);
//
//        // 添加帮助/关于菜单
//        createHelpAboutMenu(menuBar);
//    }
//
//    private void createDebugMenu(QMenuBar menuBar) {
//        if (!ExternalSwitch.parseBoolean("pbh.app-v")) {
//            if (ExternalSwitch.parseBoolean("pbh.gui.debug-tools", Main.getMeta().isSnapshotOrBeta()
//                    || "LiveDebug".equalsIgnoreCase(ExternalSwitch.parse("pbh.release")))) {
//                QMenu debugMenu = menuBar.addMenu("DEBUG");
//
//                QAction sendInfoMessage = debugMenu.addAction("[托盘通知] 发送信息托盘消息");
//                sendInfoMessage.triggered.connect(() ->
//                        qtGui.createNotification(Level.INFO, "测试（信息）", "测试消息"));
//
//                QAction sendWarningMessage = debugMenu.addAction("[托盘通知] 发送警告托盘消息");
//                sendWarningMessage.triggered.connect(() ->
//                        qtGui.createNotification(Level.WARN, "测试（警告）", "测试消息"));
//
//                QAction sendErrorMessage = debugMenu.addAction("[托盘通知] 发送错误托盘消息");
//                sendErrorMessage.triggered.connect(() ->
//                        qtGui.createNotification(Level.ERROR, "测试（错误）", "测试消息"));
//            }
//        }
//    }
//
//    // 创建程序菜单
//    private void createProgramMenu(QMenuBar menuBar) {
//        QMenu programMenu = menuBar.addMenu(tlUI(Lang.GUI_MENU_PROGRAM));
//
//        // 仅在非 app-v 环境下添加"打开数据目录"选项
//        if (!ExternalSwitch.parseBoolean("pbh.app-v", false)) {
//            QAction openDataDirAction = programMenu.addAction(tlUI(Lang.GUI_MENU_OPEN_DATA_DIRECTORY));
//            openDataDirAction.triggered.connect(() -> {
//                try {
//                    Desktop.getDesktop().open(Main.getDataDirectory());
//                } catch (IOException ex) {
//                    System.err.println(
//                            "Unable to open data directory " + Main.getDataDirectory().getPath() + " in desktop env.");
//                }
//            });
//        }
//
//        QAction switchToSwing = programMenu.addAction(tlUI(Lang.GUI_PROGRAM_SWITCH_TO_AUTO));
//        switchToSwing.triggered.connect(() -> {
//            Main.getMainConfig().set("gui", "auto");
//            try {
//                Main.getMainConfig().save(Main.getMainConfigFile());
//                System.exit(0);
//            } catch (IOException ex) {
//                log.error("Unable to switch to Auto", ex);
//            }
//        });
//
//        programMenu.addSeparator();
//
//        QAction quitAction = programMenu.addAction(tlUI(Lang.GUI_MENU_QUIT));
//        quitAction.triggered.connect(() -> System.exit(0));
//    }
//
//    // 创建 WebUI 菜单
//    private void createWebUIMenu(QMenuBar menuBar) {
//        QMenu webUiMenu = menuBar.addMenu(tlUI(Lang.GUI_MENU_WEBUI));
//
//        QAction openWebUiAction = webUiMenu.addAction(tlUI(Lang.GUI_MENU_WEBUI_OPEN));
//        openWebUiAction.triggered.connect(this::openWebUI);
//
//        QAction copyWebUiTokenAction = webUiMenu.addAction(tlUI(Lang.GUI_COPY_WEBUI_TOKEN));
//        copyWebUiTokenAction.triggered.connect(() -> {
//            if (Main.getServer() != null && Main.getServer().getWebContainer() != null) {
//                String content = Main.getServer().getWebContainer().getToken();
//                copyText(content);
//                qtGui.createDialog(Level.INFO, tlUI(Lang.GUI_COPY_TO_CLIPBOARD_TITLE),
//                        String.format(tlUI(Lang.GUI_COPY_TO_CLIPBOARD_DESCRIPTION, content)), () -> {
//                        });
//            }
//        });
//    }
//
//    // 创建帮助/关于菜单
//    private void createHelpAboutMenu(QMenuBar menuBar) {
//        QMenu helpMenu = menuBar.addMenu(tlUI(Lang.GUI_MENU_ABOUT));
//
//        QAction viewOnGithubAction = helpMenu.addAction(tlUI(Lang.ABOUT_VIEW_GITHUB));
//        viewOnGithubAction.triggered.connect(() -> qtGui.openWebpage(URI.create(tlUI(Lang.GITHUB_PAGE))));
//
//        QAction creditAction = helpMenu.addAction(tlUI(Lang.ABOUT_VIEW_CREDIT));
//        creditAction.triggered.connect(() -> {
//            var replaces = new HashMap<String, String>();
//            replaces.put("{version}", Main.getMeta().getVersion());
//            replaces.put("{username}", System.getProperty("user.name"));
//            replaces.put("{worldEndingCounter}", "365");
//
//            // 获取400年后的现在时刻, 格式化为 YYYY-MM-DD HH:mm:ss
//            var future = java.util.Calendar.getInstance();
//            future.add(java.util.Calendar.YEAR, 400);
//            replaces.put("{lastLogin}", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(future.getTime()));
//
//            // 使用Qt版本的AboutWindow
//            new QtAboutWindow(this, replaces);
//        });
//    }
//
//    // 打开 WebUI
//    private void openWebUI() {
//        if (Main.getServer() != null && Main.getServer().getWebContainer() != null) {
//            qtGui.openWebpage(URI.create("http://127.0.0.1:" + Main.getServer().getWebContainer().javalin().port() +
//                    "?token=" + Main.getServer().getWebContainer().getToken()));
//        }
//    }
//
//    // 复制文本到剪贴板
//    public static void copyText(String content) {
//        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
//        if (Main.getServer() != null && Main.getServer().getWebContainer() != null) {
//            StringSelection ts = new StringSelection(content);
//            clipboard.setContents(ts, null);
//        }
//    }
//
//    @Override
//    protected void closeEvent(QCloseEvent event) {
//        // 阻止窗口关闭，改为最小化到托盘
//        event.ignore();
//
//        if (trayManager != null) {
//            trayManager.minimizeToTray();
//        } else {
//            // 如果托盘不可用，正常关闭
//            super.closeEvent(event);
//        }
//    }
//}
