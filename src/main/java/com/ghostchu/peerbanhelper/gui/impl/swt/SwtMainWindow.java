package com.ghostchu.peerbanhelper.gui.impl.swt;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.gui.impl.swt.tabs.LogsTabComponent;
import com.ghostchu.peerbanhelper.gui.impl.swt.tabs.TabComponent;
import com.ghostchu.peerbanhelper.gui.impl.swt.tabs.WebUITabComponent;
import com.ghostchu.peerbanhelper.text.Lang;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

public class SwtMainWindow {

    private final SwtGuiImpl swtGui;
    Display display;
    Shell shell;
    TabFolder tabFolder;

    // Tab 组件
    private List<TabComponent> tabComponents = new ArrayList<>();
    private LogsTabComponent logsTabComponent;
    private WebUITabComponent webUITabComponent;

    // 图标资源
    Image iconImage; // 用于存储图标资源

    // 托盘管理器
    SwtTrayManager trayManager;

    public SwtMainWindow(SwtGuiImpl swtGui, Display display) {
        this.shell = new Shell(display);
        this.swtGui = swtGui;
        this.display = display;
        shell.setText(tlUI(Lang.GUI_TITLE_LOADING, Main.getMeta().getVersion(), Main.getMeta().getAbbrev()));
        shell.setSize(1280, 720);

        // 加载图标资源
        loadIconImage();
        // 设置窗口和任务栏图标
        if (iconImage != null) {
            shell.setImage(iconImage);
        }

        // 创建菜单栏
        createMenuBar();

        shell.setVisible(!swtGui.isSilentStart());
        // 设置 Shell 的网格布局
        shell.setLayout(new org.eclipse.swt.layout.GridLayout(1, false));
        createComponents();

        // 创建托盘管理器
        trayManager = new SwtTrayManager(display, shell, iconImage, tabComponents);

        shell.layout(true, true);
        shell.open();
        tabComponents.forEach(TabComponent::windowShow);
    }

    // 加载图标资源
    private void loadIconImage() {
        try {
            iconImage = new Image(display, Main.class.getResourceAsStream("/assets/icon.png"));
        } catch (Exception e) {
            System.err.println("无法加载图标资源: " + e.getMessage());
        }
    }

    private void createComponents() {
        this.tabFolder = new TabFolder(shell, SWT.NONE);
        this.tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // 创建并添加 Tab 组件
        logsTabComponent = new LogsTabComponent();
        webUITabComponent = new WebUITabComponent();

        tabComponents.add(logsTabComponent);
        tabComponents.add(webUITabComponent);

        // 为每个组件创建 Tab
        for (TabComponent component : tabComponents) {
            component.createTab(tabFolder);
        }
    }

    public void sync() {
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        display.dispose();
    }

    // 获取日志 Tab 组件
    public LogsTabComponent getLogsTabComponent() {
        return logsTabComponent;
    }

    // 获取 WebUI Tab 组件
    public WebUITabComponent getWebUITabComponent() {
        return webUITabComponent;
    }

    // 在窗口销毁时释放资源
    public void dispose() {
        if (trayManager != null) {
            trayManager.dispose();
        }
        if (iconImage != null && !iconImage.isDisposed()) {
            iconImage.dispose();
        }
    }

    // 创建菜单栏
    private void createMenuBar() {
        Menu menuBar = new Menu(shell, SWT.BAR);
        shell.setMenuBar(menuBar);

        // 添加程序菜单
        createProgramMenu(menuBar);

        // 添加 WebUI 菜单
        createWebUIMenu(menuBar);

        createDebugMenu(menuBar);

        // 添加帮助/关于菜单
        createHelpAboutMenu(menuBar);
    }

    private void createDebugMenu(Menu menuBar) {
        if (!ExternalSwitch.parseBoolean("pbh.app-v")) {
            if (ExternalSwitch.parseBoolean("pbh.gui.debug-tools", Main.getMeta().isSnapshotOrBeta() || "LiveDebug".equalsIgnoreCase(ExternalSwitch.parse("pbh.release")))) {
                MenuItem debugMenuItem = new MenuItem(menuBar, SWT.CASCADE);
                debugMenuItem.setText("DEBUG");

                Menu debugMenu = new Menu(shell, SWT.DROP_DOWN);
                debugMenuItem.setMenu(debugMenu);

                MenuItem sendInfoMessage = new MenuItem(debugMenu, SWT.PUSH);
                sendInfoMessage.setText("[托盘通知] 发送信息托盘消息");
                sendInfoMessage.addListener(SWT.Selection, e -> swtGui.createNotification(Level.INFO, "测试（信息）", "测试消息"));

                MenuItem sendWarningMessage = new MenuItem(debugMenu, SWT.PUSH);
                sendWarningMessage.setText("[托盘通知] 发送警告托盘消息");
                sendWarningMessage.addListener(SWT.Selection, e -> swtGui.createNotification(Level.WARNING, "测试（警告）", "测试消息"));

                MenuItem sendErrorMessage = new MenuItem(debugMenu, SWT.PUSH);
                sendErrorMessage.setText("[托盘通知] 发送错误托盘消息");
                sendErrorMessage.addListener(SWT.Selection, e -> swtGui.createNotification(Level.SEVERE, "测试（错误）", "测试消息"));

            }
        }

    }


    // 创建程序菜单
    private void createProgramMenu(Menu menuBar) {
        MenuItem programMenuItem = new MenuItem(menuBar, SWT.CASCADE);
        programMenuItem.setText(tlUI(Lang.GUI_MENU_PROGRAM));

        Menu programMenu = new Menu(shell, SWT.DROP_DOWN);
        programMenuItem.setMenu(programMenu);

        // 仅在非 app-v 环境下添加"打开数据目录"选项
        if (!ExternalSwitch.parseBoolean("pbh.app-v", false)) {
            MenuItem openDataDirItem = new MenuItem(programMenu, SWT.PUSH);
            openDataDirItem.setText(tlUI(Lang.GUI_MENU_OPEN_DATA_DIRECTORY));
            openDataDirItem.addListener(SWT.Selection, e -> {
                try {
                    Desktop.getDesktop().open(Main.getDataDirectory());
                } catch (IOException ex) {
                    System.err.println("Unable to open data directory " + Main.getDataDirectory().getPath() + " in desktop env.");
                }
            });

            new MenuItem(programMenu, SWT.SEPARATOR);
        }

        MenuItem quitItem = new MenuItem(programMenu, SWT.PUSH);
        quitItem.setText(tlUI(Lang.GUI_MENU_QUIT));
        quitItem.addListener(SWT.Selection, e -> System.exit(0));
    }

    // 创建 WebUI 菜单
    private void createWebUIMenu(Menu menuBar) {
        MenuItem webUiMenuItem = new MenuItem(menuBar, SWT.CASCADE);
        webUiMenuItem.setText(tlUI(Lang.GUI_MENU_WEBUI));

        Menu webUiMenu = new Menu(shell, SWT.DROP_DOWN);
        webUiMenuItem.setMenu(webUiMenu);

        MenuItem openWebUiItem = new MenuItem(webUiMenu, SWT.PUSH);
        openWebUiItem.setText(tlUI(Lang.GUI_MENU_WEBUI_OPEN));
        openWebUiItem.addListener(SWT.Selection, e -> openWebUI());

        MenuItem copyWebUiTokenItem = new MenuItem(webUiMenu, SWT.PUSH);
        copyWebUiTokenItem.setText(tlUI(Lang.GUI_COPY_WEBUI_TOKEN));
        copyWebUiTokenItem.addListener(SWT.Selection, e -> {
            if (Main.getServer() != null && Main.getServer().getWebContainer() != null) {
                String content = Main.getServer().getWebContainer().getToken();
                copyText(content);
                swtGui.createDialog(Level.INFO, tlUI(Lang.GUI_COPY_TO_CLIPBOARD_TITLE),
                        String.format(tlUI(Lang.GUI_COPY_TO_CLIPBOARD_DESCRIPTION, content)), () -> {
                        });
            }
        });
    }

    // 创建帮助/关于菜单
    private void createHelpAboutMenu(Menu menuBar) {
        MenuItem helpMenuItem = new MenuItem(menuBar, SWT.CASCADE);
        helpMenuItem.setText(tlUI(Lang.GUI_MENU_ABOUT));

        Menu helpMenu = new Menu(shell, SWT.DROP_DOWN);
        helpMenuItem.setMenu(helpMenu);

        MenuItem viewOnGithubItem = new MenuItem(helpMenu, SWT.PUSH);
        viewOnGithubItem.setText(tlUI(Lang.ABOUT_VIEW_GITHUB));
        viewOnGithubItem.addListener(SWT.Selection, e -> swtGui.openWebpage(URI.create(tlUI(Lang.GITHUB_PAGE))));

        MenuItem creditMenuItem = new MenuItem(helpMenu, SWT.PUSH);
        creditMenuItem.setText(tlUI(Lang.ABOUT_VIEW_CREDIT));
        creditMenuItem.addListener(SWT.Selection, e -> {
            var replaces = new HashMap<String, String>();
            replaces.put("{version}", Main.getMeta().getVersion());
            replaces.put("{username}", System.getProperty("user.name"));
            replaces.put("{worldEndingCounter}", "365");

            // 获取400年后的现在时刻, 格式化为 YYYY-MM-DD HH:mm:ss
            var future = java.util.Calendar.getInstance();
            future.add(java.util.Calendar.YEAR, 400);
            replaces.put("{lastLogin}", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(future.getTime()));

            // 使用SWT版本的AboutWindow
            new SwtAboutWindow(display, replaces);
        });
    }

    // 打开 WebUI
    private void openWebUI() {
        if (Main.getServer() != null && Main.getServer().getWebContainer() != null) {
            swtGui.openWebpage(URI.create("http://127.0.0.1:" + Main.getServer().getWebContainer().javalin().port() +
                    "?token=" + Main.getServer().getWebContainer().getToken()));
        }
    }

    // 复制文本到剪贴板
    public static void copyText(String content) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        if (Main.getServer() != null && Main.getServer().getWebContainer() != null) {
            StringSelection ts = new StringSelection(content);
            clipboard.setContents(ts, null);
        }
    }
}
