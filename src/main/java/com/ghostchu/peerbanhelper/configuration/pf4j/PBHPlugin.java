package com.ghostchu.peerbanhelper.configuration.pf4j;

import com.ghostchu.peerbanhelper.configuration.pf4j.menu.PBHPluginMenu;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

import java.util.List;

public abstract class PBHPlugin extends Plugin {
    protected PBHPlugin() {}

    protected PBHPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @NotNull
    public List<PBHPluginMenu> getPluginMenu() {
        return List.of();
    }

    @NotNull
    public TranslationComponent getPluginDisplayName() {
        return new TranslationComponent(getWrapper() == null ? getClass().getSimpleName() : getWrapper().getPluginId());
    }

    @Nullable
    public TranslationComponent getPluginDescription() {
        return null;
    }
}
