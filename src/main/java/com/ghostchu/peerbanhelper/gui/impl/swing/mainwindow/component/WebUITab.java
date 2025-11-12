package com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.gui.PBHLookAndFeelNeedReloadEvent;
import com.ghostchu.peerbanhelper.gui.PBHGuiBridge;
import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.SwingMainWindow;
import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component.swtembed.SwtBrowserCanvas;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.SharedObject;
import com.google.common.eventbus.Subscribe;
import lombok.SneakyThrows;

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
        unregisterBrowser();
        ensureBrowser();
    }


    @Override
    public void onWindowShow() {
        ensureBrowser();
    }

    @Override
    public void onWindowHide() {
        unregisterBrowser();
    }

    @SneakyThrows
    public void ensureBrowser() {
        if (webBrowser == null || !webBrowser.isValid()) {
            unregisterBrowser();
            if (SwingUtilities.isEventDispatchThread()) {
                createBrowser();
            } else {
                SwingUtilities.invokeAndWait(this::createBrowser);
            }
        }
    }

    @SneakyThrows
    private void createBrowser() {
        this.webBrowser = new SwtBrowserCanvas();
        webuiPanel.add(webBrowser, BorderLayout.CENTER);
        webBrowser.initBrowser();
        if (bridge != null) {
            navigateToIndex();
        }
    }

    @SneakyThrows
    private void unregisterBrowser() {
        if (webBrowser != null) {
            webuiPanel.removeAll();
            webBrowser = null;
        }
    }

    @Override
    public void onWindowResize() {

    }

    @Override
    public void onStarted(PBHGuiBridge bridge) {
        this.bridge = bridge;
        navigateToIndex();
    }

    private void navigateToIndex() {
        if (webBrowser != null) {
            bridge.getWebUiUrl().ifPresent(uri -> webBrowser.setUrl(uri +"&silentLogin="+ SharedObject.SILENT_LOGIN_TOKEN_FOR_GUI));
        }
    }
}
