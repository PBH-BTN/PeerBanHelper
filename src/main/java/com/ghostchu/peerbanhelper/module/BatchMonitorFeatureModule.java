package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public interface BatchMonitorFeatureModule extends FeatureModule {
    /**
     * 在某个下载器的 Peers 全部获取完成后调用，此方法必须等待下载器上的所有种子都获取完毕后才会调用
     * 注意：不保证此方法一定调用，特别是某个下载器出现阻塞超时时，可能会被全部跳过
     *
     * @param peers 下载器 -> 种子 -> 对等体列表
     */
    default void onPeersRetrieved(@NotNull Map<Downloader, Map<Torrent, List<Peer>>> peers, @NotNull PipelineTask<?> task) {
    }

    /**
     * 在某个下载器的某个种子的全部 Peers 全部获取完成后调用
     * 注意：不保证此方法一定调用，特别是某个下载器出现阻塞超时时，可能指定种子会被全部跳过
     *
     * @param downloader 下载器
     * @param torrent    种子
     * @param peers      对等体列表
     */
    default void onPeersRetrieved(@NotNull Downloader downloader, Torrent torrent, List<Peer> peers, @NotNull PipelineTask<?> task) {
    }
}
