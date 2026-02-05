package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;

public interface RuleFeatureModule extends FeatureModule {
    /**
     * 检查一个特定的 Torrent 和 Peer 是否应该封禁
     *
     * @param torrent             Torrent
     * @param peer                Peer
     * @return 规则检查结果
     */
    @NotNull
    CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader);

    /**
     * 指示模块的内部处理逻辑是否是线程安全的，如果线程不安全，PeerBanHelper 将在同步块中执行不安全的模块
     * 以避免出现线程安全错误
     *
     * @return 是否是线程安全模块
     */
    default boolean isThreadSafe() {
        return false;
    }
}
