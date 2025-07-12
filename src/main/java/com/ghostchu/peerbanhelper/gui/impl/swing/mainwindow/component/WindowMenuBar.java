package com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.WebServerStartedEvent;
import com.ghostchu.peerbanhelper.gui.PBHGuiBridge;
import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.SwingMainWindow;
import com.ghostchu.peerbanhelper.gui.impl.swing.toolwindow.AboutWindow;
import com.ghostchu.peerbanhelper.text.Lang;
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
        SwingUtilities.invokeLater(()-> parent.setJMenuBar(setupMenuBar()));
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
        parent.add(menuBar, BorderLayout.NORTH);
        return menuBar;
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
        copyWebUIToken.addActionListener(e -> bridge.getWebUiToken().ifPresent(content->{
            SwingMainWindow.copyText(content);
            parent.getSwingGUI().createDialog(Level.INFO, tlUI(Lang.GUI_COPY_TO_CLIPBOARD_TITLE), String.format(tlUI(Lang.GUI_COPY_TO_CLIPBOARD_DESCRIPTION, content)), ()->{});
        }));
        webUIMenu.add(copyWebUIToken);
        return webUIMenu;
    }
}
