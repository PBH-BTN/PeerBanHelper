package com.ghostchu.peerbanhelper.gui.impl;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

public interface GuiImpl {
    void showConfigurationSetupDialog();

    void setup();

    void createMainWindow();

    void sync();

    void close();

    default void onPBHFullyStarted(PeerBanHelperServer server) {
    }

    void createNotification(@NotNull Level level, @NotNull String title, @NotNull String description);

    void createDialog(@NotNull Level level, @NotNull String title, @NotNull String description);
}
