package com.ghostchu.peerbanhelper.gui.impl;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;

public interface GuiImpl {
    void showConfigurationSetupDialog();

    void setup();

    void createMainWindow();

    void sync();

    void close();

    default void onPBHFullyStarted(PeerBanHelperServer server) {
    }
}
