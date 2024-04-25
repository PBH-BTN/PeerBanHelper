package com.ghostchu.peerbanhelper.module;

import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

public interface FeatureModule {
    /**
     * 获取功能模块的人类可读名称
     * @return 模块可读名称
     */
    @NotNull
    String getName();

    /**
     * 获取功能模块的内部配置键名
     * @return 配置键名
     */
    @NotNull
    String getConfigName();

    boolean isModuleEnabled();

    ConfigurationSection getConfig();

    /**
     * 功能模块启用回调
     */
    void onEnable();

    /**
     * 功能模块禁用回调
     */
    void onDisable();

    /**
     * 功能模块启用序列
     */
    void enable();

    /**
     * 功能模块禁用序列
     */
    void disable();


}
