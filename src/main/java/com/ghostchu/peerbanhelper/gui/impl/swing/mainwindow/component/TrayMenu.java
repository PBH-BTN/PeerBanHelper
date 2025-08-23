package com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.event.PBHServerStartedEvent;
import com.ghostchu.peerbanhelper.gui.PBHGuiBridge;
import com.ghostchu.peerbanhelper.gui.impl.swing.SwingTray;
import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.SwingMainWindow;
import com.ghostchu.peerbanhelper.text.Lang;
import com.google.common.eventbus.Subscribe;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

public class TrayMenu {
    private final SwingMainWindow parent;
    private boolean persistFlagTrayMessageSent;
    @Nullable
    @Getter
    private SwingTray swingTrayDialog;
    private PBHGuiBridge bridge;

    public TrayMenu(SwingMainWindow parent) {
        this.parent = parent;
        setupSystemTray();
        Main.getEventBus().register(this);
    }


    @Subscribe
    public void onPeerBanHelperStarted(PBHServerStartedEvent event) {
        this.bridge = Main.getApplicationContext().getBean(PBHGuiBridge.class);
        SwingUtilities.invokeLater(this::updateTrayMenus);
        //CommonUtil.getScheduler().scheduleAtFixedRate(this::updateTrayMenus, 0, 1, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void setupSystemTray() {
        if (SystemTray.isSupported()) {
            TrayIcon icon = new TrayIcon(Toolkit.getDefaultToolkit().getImage(Main.class.getResource("/assets/icon.png")));
            icon.setImageAutoSize(true);
            SystemTray sysTray = SystemTray.getSystemTray();//获取系统托盘
            try {
                var tray = new SwingTray(icon, mouseEvent -> parent.setVisible(true), mouseEvent -> updateTrayMenus());
                sysTray.add(icon);//将托盘图表添加到系统托盘
                updateTrayMenus();
                this.swingTrayDialog = tray;
            } catch (AWTException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void minimizeToTray() {
        if (swingTrayDialog != null) {
            parent.setVisible(false);
            if (!persistFlagTrayMessageSent) {
                persistFlagTrayMessageSent = true;
                parent.getSwingGUI().createNotification(Level.INFO, tlUI(Lang.GUI_TRAY_MESSAGE_CAPTION), tlUI(Lang.GUI_TRAY_MESSAGE_DESCRIPTION));
            }
        }
    }

    private void updateTrayMenus() {
        if (swingTrayDialog == null) return;
        List<JMenuItem> items = new ArrayList<>();
        JMenuItem openMainWindow = new JMenuItem(tlUI(Lang.GUI_MENU_SHOW_WINDOW), new FlatSVGIcon(Main.class.getResource("/assets/icon/tray/open.svg")));
        JMenuItem openWebUI = new JMenuItem(tlUI(Lang.GUI_MENU_WEBUI_OPEN), new FlatSVGIcon(Main.class.getResource("/assets/icon/tray/browser.svg")));
        JMenuItem quit = new JMenuItem(tlUI(Lang.GUI_MENU_QUIT), new FlatSVGIcon(Main.class.getResource("/assets/icon/tray/close.svg")));
        openMainWindow.addActionListener(e -> parent.setVisible(true));
        openWebUI.addActionListener(e -> parent.openWebUI());
        quit.addActionListener(e -> System.exit(0));
        items.add(menuDisplayItem(new JMenuItem(tlUI(Lang.GUI_MENU_STATS))));
        items.add(menuBanStats());
        items.add(menuDownloaderStats());
        items.add(menuDisplayItem(new JMenuItem(tlUI(Lang.GUI_MENU_QUICK_OPERATIONS))));
        items.add(openMainWindow);
        items.add(openWebUI);
        items.add(null);
        items.add(quit);
        swingTrayDialog.set(items);
    }

    private JMenuItem menuDownloaderStats() {
        var totalDownloaders = 0L;
        var healthDownloaders = 0L;
        if (Main.getServer() != null) {
            totalDownloaders = Main.getServer().getDownloaderManager().getDownloaders().size();
            healthDownloaders = Main.getServer().getDownloaderManager().getDownloaders().stream().filter(m -> m.getLastStatus() == DownloaderLastStatus.HEALTHY).count();
        }
        return new JMenuItem(tlUI(Lang.GUI_MENU_STATS_DOWNLOADER, healthDownloaders, totalDownloaders), new FlatSVGIcon(Main.class.getResource("/assets/icon/tray/connection.svg")));
    }

    private JMenuItem menuBanStats() {
        var bannedPeers = 0L;
        var bannedIps = 0L;
        var server = Main.getServer();
        if (server != null) {
            bannedIps = Main.getServer().getDownloaderServer().getBanList().directAccess().values().stream().map(m -> m.getPeer().getAddress().getIp()).distinct().count();
            bannedPeers = Main.getServer().getDownloaderServer().getBanList().directAccess().size();
        }
        return new JMenuItem(tlUI(Lang.GUI_MENU_STATS_BANNED, bannedPeers, bannedIps), new FlatSVGIcon(Main.class.getResource("/assets/icon/tray/banned.svg")));
    }

    private JMenuItem menuDisplayItem(JMenuItem jMenuItem) {
        jMenuItem.setEnabled(false);
        return jMenuItem;
    }

}
