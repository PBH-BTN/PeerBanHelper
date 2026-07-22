package com.ghostchu.peerbanhelper.configuration.pf4j.menu;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import org.jetbrains.annotations.NotNull;

public interface PBHPluginMenu {
    @NotNull
    String getMenuId();

    @NotNull
    TranslationComponent getDisplayName();

    boolean isDisabled();

    @NotNull
    String getRelativeEndpoint();
}
