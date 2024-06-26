package com.ghostchu.peerbanhelper.gui;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

public interface GuiManager {
    void setup();

    boolean isGuiAvailable();

    void showConfigurationSetupDialog();

    void createMainWindow();

    void sync();

    void close();

    void onPBHFullyStarted(PeerBanHelperServer server);

    void createNotification(@NotNull Level level, @NotNull String title, @NotNull String description);

    void createDialog(@NotNull Level level, @NotNull String title, @NotNull String description);
}
