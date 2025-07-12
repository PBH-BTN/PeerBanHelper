package com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.extras.components.FlatButton;
import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.database.table.AlertEntity;
import com.ghostchu.peerbanhelper.event.WebServerStartedEvent;
import com.ghostchu.peerbanhelper.gui.PBHGuiBridge;
import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.SwingMainWindow;
import com.ghostchu.peerbanhelper.gui.impl.swing.toolwindow.AboutWindow;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.google.common.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class WindowMenuBar {
    private final SwingMainWindow parent;
    private PBHGuiBridge bridge;

    public WindowMenuBar(SwingMainWindow parent) {
        this.parent = parent;
        updateMenuBar();
        Main.getEventBus().register(this);
    }

    @Subscribe
    public void onWebServerStarted(WebServerStartedEvent event) {
        this.bridge = Main.getApplicationContext().getBean(PBHGuiBridge.class);
        updateMenuBar();
    }

    private void updateMenuBar() {
        SwingUtilities.invokeLater(() -> parent.setJMenuBar(setupMenuBar()));
    }

    private JMenuBar setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(generateProgramMenu());
        menuBar.add(generateWebUIMenu());
        if (!ExternalSwitch.parseBoolean("pbh.app-v")) {
            if (ExternalSwitch.parseBoolean("pbh.gui.debug-tools", Main.getMeta().isSnapshotOrBeta() || "LiveDebug".equalsIgnoreCase(ExternalSwitch.parse("pbh.release")))) {
                // menuBar.add(generateDebugMenu());
            }
        }
        //menuBar.add(Box.createGlue());
        menuBar.add(generateHelpAbout());
        menuBar.add(Box.createGlue());
        menuBar.add(generateAlertsButton());
        parent.add(menuBar, BorderLayout.NORTH);
        return menuBar;
    }

    private Component generateAlertsButton() {
        FlatButton alertButton = new FlatButton();
        alertButton.setButtonType(FlatButton.ButtonType.toolBarButton);
        alertButton.setFocusable(false);
        alertButton.addActionListener(event -> {
            bridge.getWebUiUrl().ifPresent(uri-> parent.getSwingGUI().openWebpage(uri));
        });
        CommonUtil.getScheduler().scheduleAtFixedRate(() -> {
            if (bridge == null) {
                alertButton.setEnabled(false);
                return;
            }
            var alerts = bridge.getAlerts();
            String text = alerts.isEmpty() ? "" : String.valueOf(alerts.size());
            // found most important level by AlertLevel order
            AlertLevel highestLevel = alerts.stream()
                    .map(AlertEntity::getLevel)
                    .max(AlertLevel::compareTo)
                    .orElse(null);
            SwingUtilities.invokeLater(() -> {
                alertButton.setText(text);
                if (highestLevel == null || alerts.isEmpty()) {
                    alertButton.setBackground(new Color(0,0,0,1));
                    alertButton.setForeground(null);
                    alertButton.setIcon(new FlatSVGIcon(Main.class.getResource("/assets/icon/common/alert.svg")));
                    alertButton.setEnabled(false);
                } else {
                    alertButton.setEnabled(true);
                    alertButton.setIcon(new FlatSVGIcon(Main.class.getResource("/assets/icon/common/alert_white.svg")));
                    var bgColor = switch (highestLevel) {
                        case INFO -> Color.decode("#0969da");
                        case WARN -> Color.decode("#bc4c00");
                        case ERROR, FATAL -> Color.decode("#cf222e");
                        case TIP -> Color.decode("#1f883d");
                    };
                    alertButton.setBackground(bgColor);
                    alertButton.setForeground(Color.WHITE);
                }
            });
        }, 0L, 1L, TimeUnit.SECONDS);
        // alertButton.addActionListener( e -> JOptionPane.showMessageDialog( null, "Hello User! How are you?", "User", JOptionPane.INFORMATION_MESSAGE ) );
        return alertButton;
    }

    private Component generateHelpAbout() {
        JMenu menu = new JMenu(tlUI(Lang.GUI_MENU_ABOUT));
        JMenuItem creditMenu = new JMenuItem(tlUI(Lang.ABOUT_VIEW_CREDIT));
        creditMenu.addActionListener(e -> {
            var replaces = new HashMap<String, String>();
            replaces.put("{version}", Main.getMeta().getVersion());
            replaces.put("{username}", System.getProperty("user.name"));
            replaces.put("{worldEndingCounter}", "365");
            // 获取400年后的现在时刻, 格式化为 YYYY-MM-DD HH:mm:ss
            var future = Calendar.getInstance();
            future.add(Calendar.YEAR, 400);
            replaces.put("{lastLogin}", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(future.getTime()));
            var window = new AboutWindow(replaces);
        });
        JMenuItem viewOnGithub = new JMenuItem(tlUI(Lang.ABOUT_VIEW_GITHUB));
        viewOnGithub.addActionListener(e -> parent.getSwingGUI().openWebpage(URI.create(tlUI(Lang.GITHUB_PAGE))));
        menu.add(viewOnGithub);
        menu.add(creditMenu);
        return menu;
    }

    private Component generateProgramMenu() {
        JMenu menu = new JMenu(tlUI(Lang.GUI_MENU_PROGRAM));
        JMenuItem openDataDirectory = new JMenuItem(tlUI(Lang.GUI_MENU_OPEN_DATA_DIRECTORY));
        openDataDirectory.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(Main.getDataDirectory());
            } catch (IOException ex) {
                log.warn("Unable to open data directory {} in desktop env.", Main.getDataDirectory().getPath());
            }
        });
        if (!ExternalSwitch.parseBoolean("pbh.app-v")) {
            menu.add(openDataDirectory);
        }
        menu.addSeparator();
        JMenuItem quit = new JMenuItem(tlUI(Lang.GUI_MENU_QUIT));
        quit.addActionListener(e -> System.exit(0));
        menu.add(quit);
        return menu;
    }

    private JMenu generateWebUIMenu() {
        JMenu webUIMenu = new JMenu(tlUI(Lang.GUI_MENU_WEBUI));
        JMenuItem openWebUIMenuItem = new JMenuItem(tlUI(Lang.GUI_MENU_WEBUI_OPEN));
        openWebUIMenuItem.addActionListener(e -> parent.openWebUI());
        openWebUIMenuItem.setEnabled(bridge != null && bridge.getWebUiUrl().isPresent());
        webUIMenu.add(openWebUIMenuItem);
        JMenuItem copyWebUIToken = new JMenuItem(tlUI(Lang.GUI_COPY_WEBUI_TOKEN));
        copyWebUIToken.setEnabled(bridge != null && bridge.getWebUiToken().isPresent());
        copyWebUIToken.addActionListener(e -> bridge.getWebUiToken().ifPresent(content -> {
            SwingMainWindow.copyText(content);
            parent.getSwingGUI().createDialog(Level.INFO, tlUI(Lang.GUI_COPY_TO_CLIPBOARD_TITLE), String.format(tlUI(Lang.GUI_COPY_TO_CLIPBOARD_DESCRIPTION, content)), () -> {
            });
        }));
        webUIMenu.add(copyWebUIToken);
        return webUIMenu;
    }
}
