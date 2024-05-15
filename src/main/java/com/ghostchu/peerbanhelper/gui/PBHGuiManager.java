package com.ghostchu.peerbanhelper.gui;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.gui.impl.GuiImpl;

import java.awt.*;
import java.util.logging.Level;

public class PBHGuiManager implements GuiManager {
    private final GuiImpl gui;

    public PBHGuiManager(GuiImpl gui) {
        this.gui = gui;
    }

    @Override
    public void setup() {
        gui.setup();
    }


    @Override
    public boolean isGuiAvailable() {
        return Desktop.isDesktopSupported();
    }

    @Override
    public void showConfigurationSetupDialog() {
        gui.showConfigurationSetupDialog();
    }

    @Override
    public void createMainWindow() {
        gui.createMainWindow();
    }

    @Override
    public void sync() {
        gui.sync();
    }

    @Override
    public void close() {
        gui.close();
    }

    @Override
    public void onPBHFullyStarted(PeerBanHelperServer server) {
        gui.onPBHFullyStarted(server);
    }

    @Override
    public void createNotification(Level level, String title, String description) {
        gui.createNotification(level, title, description);
    }
}
