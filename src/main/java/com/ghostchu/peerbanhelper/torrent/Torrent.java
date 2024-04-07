package com.ghostchu.peerbanhelper.torrent;

public interface Torrent {
    /**
     * 获取该 Torrent 的唯一标识符
     * @return 唯一标识符值，具体因下载器实现而异
     */
    String getId();

    /**
     * 获取该 Torrent 的名称
     * @return Torrent 名称
     */
    String getName();

    /**
     * 获取首选哈希值
     * @return 首选哈希值
     */
    String getHash();

    /**
     * 获取目前该 Torrent 的共计大小
     * @return 共计大小
     */
    long getSize();

}
