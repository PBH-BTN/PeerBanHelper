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

    /**
     * 重新加载所有模块的启用状态
     * 根据配置文件的 enabled 字段，自动启用或禁用模块
     */
    void reloadModuleStates();
}
