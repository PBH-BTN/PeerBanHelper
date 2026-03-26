package com.ghostchu.peerbanhelper.configuration.pf4j;

import com.ghostchu.peerbanhelper.configuration.pf4j.menu.PBHPluginMenu;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import org.jetbrains.annotations.NotNull;
import org.pf4j.Plugin;

import java.util.List;

public abstract class PBHPlugin extends Plugin {
    @NotNull
    public abstract List<PBHPluginMenu> getPluginMenu();

    @NotNull
    public abstract TranslationComponent getPluginDisplayName();
}
