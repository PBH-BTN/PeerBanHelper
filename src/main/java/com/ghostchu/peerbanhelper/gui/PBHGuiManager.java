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

    @Override
    public void createDialog(Level level, String title, String description) {
        gui.createDialog(level, title, description);
    }

    @Override
    public ProgressDialog createProgressDialog(String title, String description, String buttonText, Runnable buttonEvent, boolean allowCancel) {
        return gui.createProgressDialog(title, description, buttonText, buttonEvent, allowCancel);
    }

    @Override
    public TaskbarControl taskbarControl() {
        return gui.taskbarControl();
    }
}
