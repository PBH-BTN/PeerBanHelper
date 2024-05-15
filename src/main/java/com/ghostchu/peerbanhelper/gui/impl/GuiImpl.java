package com.ghostchu.peerbanhelper.gui.impl;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;

import java.util.logging.Level;

public interface GuiImpl {
    void showConfigurationSetupDialog();

    void setup();

    void createMainWindow();

    void sync();

    void close();

    default void onPBHFullyStarted(PeerBanHelperServer server) {
    }

    void createNotification(Level level, String title, String description);
}
