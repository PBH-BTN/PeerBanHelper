package com.ghostchu.peerbanhelper.gui;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;

public interface GuiManager {
    void setup();

    boolean isGuiAvailable();

    void showConfigurationSetupDialog();

    void createMainWindow();

    void sync();

    void close();

    void onPBHFullyStarted(PeerBanHelperServer server);
}
