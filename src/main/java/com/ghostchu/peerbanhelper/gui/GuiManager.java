package com.ghostchu.peerbanhelper.gui;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;

import java.util.logging.Level;

public interface GuiManager {
    void setup();

    boolean isGuiAvailable();

    void createMainWindow();

    void sync();

    void close();

    void onPBHFullyStarted(PeerBanHelperServer server);

    void createNotification(Level level, String title, String description);

    void createDialog(Level level, String title, String description);

    ProgressDialog createProgressDialog(String title, String description, String buttonText, Runnable buttonEvent, boolean allowCancel);

    TaskbarControl taskbarControl();
}
