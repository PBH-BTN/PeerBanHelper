package com.ghostchu.peerbanhelper.torrent;

import com.ghostchu.peerbanhelper.util.InfoHashUtil;

public interface Torrent {
    /**
     * 获取该 Torrent 的唯一标识符
     *
     * @return 唯一标识符值，具体因下载器实现而异
     */
    String getId();

    /**
     * 获取该 Torrent 的名称
     *
     * @return Torrent 名称
     */
    String getName();

    /**
     * 获取首选哈希值
     *
     * @return 首选哈希值
     */
    String getHash();

    /**
     * 获取下载器任务进度
     *
     * @return 下载器任务进度
     */
    double getProgress();

    /**
     * 获取该 Torrent 的总大小
     *
     * @return 总大小
     */
    long getSize();

    /**
     * 获取该 Torrent 已保存的数据量 (也就是最大可以提供的上传量)
     *
     * @return 已保存的数据量
     */
    long getCompletedSize();

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
     * 是否是私有种子
     *
     * @return 私有种子
     */
    boolean isPrivate();

    /**
     * 获取种子不可逆匿名识别符
     *
     * @return 不可逆匿名识别符
     */
    default String getHashedIdentifier() {
        return InfoHashUtil.getHashedIdentifier(getHash());
    }

}
