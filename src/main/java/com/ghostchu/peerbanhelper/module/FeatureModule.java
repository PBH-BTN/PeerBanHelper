package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;

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

    /**
     * 模块检查结果是否可被缓存
     *
     * @return 可否被缓存
     */
    boolean isCheckCacheable();

    /**
     * 模块是否需要首先进行握手检查
     * @return 是否先进行握手检查
     */
    boolean needCheckHandshake();

    /**
     * 检查一个特定的 Torrent 和 Peer 是否应该封禁
     * @param torrent Torrent
     * @param peer Peer
     * @param ruleExecuteExecutor 如果需要并发执行任务，请在给定的执行器中执行，以接受线程池的约束避免资源消耗失控
     * @return 规则检查结果
     */
    @NotNull
    BanResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor);

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
