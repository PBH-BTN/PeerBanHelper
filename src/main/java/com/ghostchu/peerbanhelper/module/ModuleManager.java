package com.ghostchu.peerbanhelper.module;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ModuleManager {
    void register(@NotNull Class<? extends FeatureModule> moduleClass);

    void register(@NotNull FeatureModule module, String beanName);

    boolean unregister(@NotNull FeatureModule module);

    void unregister(@NotNull Class<FeatureModule> module);

    void unregisterAll();

    @NotNull List<FeatureModule> getModules();

    void loadPlugin();
}
