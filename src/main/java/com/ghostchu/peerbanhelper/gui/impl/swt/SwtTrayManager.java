package com.ghostchu.peerbanhelper.gui.impl.swt;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.event.PBHServerStartedEvent;
import com.ghostchu.peerbanhelper.gui.impl.swt.tabs.TabComponent;
import com.ghostchu.peerbanhelper.text.Lang;
import com.google.common.eventbus.Subscribe;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.slf4j.event.Level;

import java.awt.*;
import java.net.URI;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

public class SwtTrayManager {
    private final Display display;
    private final Shell shell;
    private final List<TabComponent> tabComponents;

    // 托盘相关的成员变量
    private TrayItem trayItem;
    private boolean notificationShown = false; // 跟踪通知是否已经显示过
    private final Image iconImage; // 用于存储图标资源

    // 托盘菜单相关成员变量
    private Menu trayMenu;
    private MenuItem banStatsItem;
    private MenuItem downloaderStatsItem;

    public SwtTrayManager(Display display, Shell shell, Image iconImage, List<TabComponent> tabComponents) {
        this.display = display;
        this.shell = shell;
        this.iconImage = iconImage;
        this.tabComponents = tabComponents;

        // 创建托盘图标
        createTrayIcon();

        // 设置窗口关闭监听器
        setupCloseListener();
        
        // 注册到事件总线
        Main.getEventBus().register(this);
    }

    // 创建托盘图标
    private void createTrayIcon() {
        final Tray tray = display.getSystemTray();
        if (tray == null) {
            return;
        }

        trayItem = new TrayItem(tray, SWT.NONE);

        // 设置托盘图标
        if (iconImage != null) {
            trayItem.setImage(iconImage);
        }
        trayItem.setToolTipText(tlUI(Lang.GUI_TRAY_TITLE));

        // 添加托盘图标点击事件
        trayItem.addListener(SWT.Selection, event -> {
            if (!shell.isVisible()) {
                shell.setVisible(true);
                shell.setMinimized(false);
                shell.forceActive();
                tabComponents.forEach(TabComponent::windowShow);
            } else {
                shell.setVisible(false);
                tabComponents.forEach(TabComponent::windowHide);
            }
        });

        // 添加托盘右键菜单
        trayMenu = new Menu(shell, SWT.POP_UP);

        // 统计信息 - 标题
        MenuItem statsTitle = new MenuItem(trayMenu, SWT.PUSH);
        statsTitle.setText(tlUI(Lang.GUI_MENU_STATS));
        statsTitle.setEnabled(false);

        // 禁止统计
        banStatsItem = new MenuItem(trayMenu, SWT.PUSH);
        banStatsItem.setEnabled(false);
        updateBanStats();

        // 下载器统计
        downloaderStatsItem = new MenuItem(trayMenu, SWT.PUSH);
        downloaderStatsItem.setEnabled(false);
        updateDownloaderStats();

        // 快捷操作 - 标题
        MenuItem quickOpsTitle = new MenuItem(trayMenu, SWT.PUSH);
        quickOpsTitle.setText(tlUI(Lang.GUI_MENU_QUICK_OPERATIONS));
        quickOpsTitle.setEnabled(false);

        // 打开主窗口
        MenuItem showItem = new MenuItem(trayMenu, SWT.PUSH);
        showItem.setText(tlUI(Lang.GUI_MENU_SHOW_WINDOW));
        showItem.addListener(SWT.Selection, event -> {
            shell.setVisible(true);
            tabComponents.forEach(TabComponent::windowShow);
            shell.setMinimized(false);
            shell.forceActive();
        });

        // WebUI 菜单项
        MenuItem webUIItem = new MenuItem(trayMenu, SWT.PUSH);
        webUIItem.setText(tlUI(Lang.GUI_MENU_WEBUI_OPEN));
        webUIItem.addListener(SWT.Selection, event -> openWebUI());

        // 分隔线
        new MenuItem(trayMenu, SWT.SEPARATOR);

        // 退出应用
        MenuItem exitItem = new MenuItem(trayMenu, SWT.PUSH);
        exitItem.setText(tlUI(Lang.GUI_MENU_QUIT));
        exitItem.addListener(SWT.Selection, event -> {
            shell.dispose();
            System.exit(0);
        });

        // 设置右键菜单并更新状态
        trayItem.addListener(SWT.MenuDetect, event -> {
            updateTrayMenus();
            trayMenu.setVisible(true);
        });
    }

    // 设置窗口关闭事件监听器
    private void setupCloseListener() {
        shell.addListener(SWT.Close, event -> {
            event.doit = false;
            // 阻止窗口关闭
            shell.setVisible(false);
            tabComponents.forEach(TabComponent::windowHide);
            if (!notificationShown && trayItem != null) {
                createNotification(Level.INFO,
                        tlUI(Lang.GUI_TRAY_MESSAGE_CAPTION),
                        tlUI(Lang.GUI_TRAY_MESSAGE_DESCRIPTION));
                // 标记通知已经显示过
                notificationShown = true;
            }
        });
    }

    /**
     * 创建并显示托盘通知
     *
     * @param level 日志级别，决定通知图标样式
     * @param title 通知标题
     * @param description 通知内容
     */
    public void createNotification(Level level, String title, String description) {
        if (trayItem == null || trayItem.isDisposed() || shell.isDisposed()) {
            return;
        }
        // 根据日志级别设置不同的图标样式
        int imageIcon = 0;
        if (level == Level.ERROR) {
            imageIcon = SWT.ICON_ERROR;
        } else if (level == Level.WARN) {
            imageIcon = SWT.ICON_WARNING;
        } else {
            imageIcon = SWT.ICON_INFORMATION;
        }

        int finalImageIcon = imageIcon;
        Display.getDefault().asyncExec(() -> {
            ToolTip tooltip = new ToolTip(shell, SWT.BALLOON | finalImageIcon);
            tooltip.setText(title);
            tooltip.setMessage(description);
            trayItem.setToolTip(tooltip);
            tooltip.setVisible(true);
        });
    }

    // 打开WebUI的方法
    private void openWebUI() {
        if (Main.getServer() != null && Main.getServer().getWebContainer() != null) {
            try {
                Desktop.getDesktop().browse(
                        URI.create("http://127.0.0.1:" + Main.getServer().getWebContainer().javalin().port() +
                                "?token=" + Main.getServer().getWebContainer().getToken())
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 更新托盘菜单
    public void updateTrayMenus() {
        updateBanStats();
        updateDownloaderStats();
    }

    // 更新禁止统计菜单项
    private void updateBanStats() {
        long bannedPeers = 0L;
        long bannedIps = 0L;
        var server = Main.getServer();
        if (server != null) {
            bannedIps = server.getDownloaderServer().getBannedPeers().values().stream()
                    .map(m -> m.getPeer().getAddress().getIp())
                    .distinct().count();
            bannedPeers = server.getDownloaderServer().getBannedPeers().size();
        }
        if (banStatsItem != null && !banStatsItem.isDisposed()) {
            banStatsItem.setText(tlUI(Lang.GUI_MENU_STATS_BANNED, bannedPeers, bannedIps));
        }
    }

    // 更新下载器统计菜单项
    private void updateDownloaderStats() {
        long totalDownloaders = 0L;
        long healthDownloaders = 0L;
        var server = Main.getServer();
        if (server != null) {
            totalDownloaders = server.getDownloaderManager().getDownloaders().size();
            healthDownloaders = server.getDownloaderManager().getDownloaders().stream()
                    .filter(m -> m.getLastStatus() == DownloaderLastStatus.HEALTHY).count();
        }
        if (downloaderStatsItem != null && !downloaderStatsItem.isDisposed()) {
            downloaderStatsItem.setText(tlUI(Lang.GUI_MENU_STATS_DOWNLOADER, healthDownloaders, totalDownloaders));
        }
    }

    // 监听服务器启动事件
    @Subscribe
    public void onPeerBanHelperStarted(PBHServerStartedEvent event) {
        if (display != null && !display.isDisposed()) {
            display.asyncExec(this::updateTrayMenus);
        }
    }

    // 资源释放
    public void dispose() {
        Main.getEventBus().unregister(this);
        if (trayItem != null && !trayItem.isDisposed()) {
            trayItem.dispose();
        }
        if (trayMenu != null && !trayMenu.isDisposed()) {
            trayMenu.dispose();
        }
    }
}
