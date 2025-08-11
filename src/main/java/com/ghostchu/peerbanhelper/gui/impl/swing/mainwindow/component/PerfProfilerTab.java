package com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.PBHLookAndFeelNeedReloadEvent;
import com.ghostchu.peerbanhelper.gui.PBHGuiBridge;
import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.SwingMainWindow;
import com.ghostchu.peerbanhelper.gui.impl.swing.mainwindow.component.swtembed.SwtBrowserCanvas;
import com.ghostchu.peerbanhelper.text.Lang;
import com.google.common.eventbus.Subscribe;
import com.sk89q.warmroast.WarmRoastManager;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import lombok.SneakyThrows;
import org.slf4j.event.Level;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

public class PerfProfilerTab implements WindowTab {
    private final SwingMainWindow parent;
    private final JPanel webuiPanel;
    private final JPanel mainPanel;
    private SwtBrowserCanvas webBrowser;
    private PBHGuiBridge bridge;

    public PerfProfilerTab(SwingMainWindow parent) {
        this.parent = parent;
        this.mainPanel = new JPanel(new BorderLayout());
        this.webuiPanel = new JPanel(new BorderLayout());
        mainPanel.add(webuiPanel, BorderLayout.CENTER);
        mainPanel.add(createToolbar(), BorderLayout.NORTH);
        parent.getTabbedPane().addTab(tlUI(Lang.GUI_TABBED_PERF), mainPanel);
        Main.getEventBus().register(this);
    }

    private JPanel createToolbar() {
        var panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        var resetButton = new JButton(tlUI(Lang.PERF_RESTART));
        resetButton.addActionListener(e -> {
            WarmRoastManager.stopAndReset();
            try {
                WarmRoastManager.start();
                Main.getGuiManager().createDialog(Level.INFO, tlUI(Lang.PERF_RESTARTED_TITLE), tlUI(Lang.PERF_RESTARTED_DESCRIPTION), () -> {
                });
            } catch (IOException | AttachNotSupportedException | AgentLoadException | AgentInitializationException ex) {
                throw new RuntimeException(ex);
            }
        });
        var refreshButton = new JButton(tlUI(Lang.PERF_REFRESH));
        refreshButton.addActionListener(e -> {
            if (webBrowser != null) {
                webBrowser.refresh();
            }
        });
        panel.add(resetButton);
        panel.add(refreshButton);
        return panel;
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
            bridge.getPerfUrl().ifPresent(uri -> webBrowser.setUrl(uri.toString()));
        }
    }
}
