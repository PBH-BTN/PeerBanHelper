package com.ghostchu.peerbanhelper.torrent;

import com.ghostchu.peerbanhelper.util.time.InfoHashUtil;
import org.jetbrains.annotations.NotNull;

public interface Torrent {
    /**
     * 获取该 Torrent 的唯一标识符
     *
     * @return 唯一标识符值，具体因下载器实现而异
     */
    @NotNull
    String getId();

    /**
     * 获取该 Torrent 的名称
     *
     * @return Torrent 名称
     */
    @NotNull
    String getName();

    /**
     * 获取首选哈希值
     *
     * @return 首选哈希值
     */
    @NotNull
    String getHash();

    /**
     * 获取下载器任务进度
     *
     * @return 下载器任务进度
     */
    double getProgress();

    /**
     * 获取目前该 Torrent 的共计大小
     *
     * @return 共计大小
     */
    long getSize();

    /**
     * 实时下载速度
     *
     * @return 实时下载速度
     */
    long getRtUploadSpeed();

    /**
     * 实时上传速度
     *
     * @return 实时上传速度
     */
    long getRtDownloadSpeed();

    /**
     * 获取种子不可逆匿名识别符
     *
     * @return 不可逆匿名识别符
     */
    @NotNull
    default String getHashedIdentifier() {
        return InfoHashUtil.getHashedIdentifier(getHash());
    }

}
