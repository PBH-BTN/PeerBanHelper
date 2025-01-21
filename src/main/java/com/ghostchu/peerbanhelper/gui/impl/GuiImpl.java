package com.ghostchu.peerbanhelper.gui.impl;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.gui.ProgressDialog;
import com.ghostchu.peerbanhelper.gui.TaskbarControl;

import java.util.logging.Level;

public interface GuiImpl {
    void setup();

    void createMainWindow();

    void sync();

    void close();

    default void onPBHFullyStarted(PeerBanHelperServer server) {
    }

    void createNotification(Level level, String title, String description);

    void createDialog(Level level, String title, String description);

    ProgressDialog createProgressDialog(String title, String description, String buttonText, Runnable buttonEvent, boolean allowCancel);

    TaskbarControl taskbarControl();
}
