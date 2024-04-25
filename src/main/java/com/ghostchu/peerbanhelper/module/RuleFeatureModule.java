package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;

public interface RuleFeatureModule extends FeatureModule {
    /**
     * 模块检查结果是否可被缓存
     *
     * @return 可否被缓存
     */
    boolean isCheckCacheable();

    /**
     * 模块是否需要首先进行握手检查
     *
     * @return 是否先进行握手检查
     */
    boolean needCheckHandshake();

    /**
     * 检查一个特定的 Torrent 和 Peer 是否应该封禁
     *
     * @param torrent             Torrent
     * @param peer                Peer
     * @param ruleExecuteExecutor 如果需要并发执行任务，请在给定的执行器中执行，以接受线程池的约束避免资源消耗失控
     * @return 规则检查结果
     */
    @NotNull
    BanResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor);
}
