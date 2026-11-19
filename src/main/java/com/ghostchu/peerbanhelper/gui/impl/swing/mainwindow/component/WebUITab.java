package com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.gui.PBHLookAndFeelNeedReloadEvent;
import com.ghostchu.peerbanhelper.gui.PBHGuiBridge;
import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.SwingMainWindow;
import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component.swtembed.SwtBrowserCanvas;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.SharedObject;
import com.google.common.eventbus.Subscribe;

import javax.swing.*;
import java.awt.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

public class WebUITab implements WindowTab {
    private final SwingMainWindow parent;
    private final JPanel webuiPanel;
    private SwtBrowserCanvas webBrowser;
    private PBHGuiBridge bridge;

    public WebUITab(SwingMainWindow parent) {
        this.parent = parent;
        this.webuiPanel = new JPanel(new BorderLayout());
        parent.getTabbedPane().addTab(tlUI(Lang.GUI_TABBED_WEBUI), webuiPanel);
        Main.getEventBus().register(this);
    }

    @Subscribe
    public void onLAFReload(PBHLookAndFeelNeedReloadEvent event) {
        runOnEdt(() -> {
            unregisterBrowser();
            ensureBrowser();
        });
    }

    @Override
    public void onWindowShow() {
        runOnEdt(this::ensureBrowser);
    }

    @Override
    public void onWindowHide() {
        runOnEdt(this::unregisterBrowser);
    }

    private void ensureBrowser() {
        if (parent.isVisible() && (webBrowser == null || webBrowser.isTerminated())) {
            unregisterBrowser();
            webBrowser = new SwtBrowserCanvas(parent.getSwingGUI().isDarkMode(), getBrowserUrl());
            webuiPanel.add(webBrowser, BorderLayout.CENTER);
            webuiPanel.revalidate();
            webuiPanel.repaint();
        }
    }

    private void unregisterBrowser() {
        if (webBrowser != null) {
            webuiPanel.remove(webBrowser);
            webBrowser = null;
            webuiPanel.revalidate();
            webuiPanel.repaint();
        }
    }

    @Override
    public void onWindowResize() {

    }

    @Override
    public void onStarted(PBHGuiBridge bridge) {
        runOnEdt(() -> {
            this.bridge = bridge;
            if (webBrowser != null) {
                webBrowser.setUrl(getBrowserUrl());
            }
        });
    }

    private String getBrowserUrl() {
        if (bridge != null) {
            return bridge.getWebUiUrl()
                    .map(uri -> uri + "&silentLogin=" + SharedObject.SILENT_LOGIN_TOKEN_FOR_GUI)
                    .orElseGet(Main::getPbhServerAddress);
        }
        return Main.getPbhServerAddress();
    }

    private void runOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
