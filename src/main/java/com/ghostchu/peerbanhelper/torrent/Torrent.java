package com.ghostchu.peerbanhelper.torrent;

import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

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
     * 获取下载器任务进度
     * @return 下载器任务进度
     */
    double getProgress();

    /**
     * 获取目前该 Torrent 的共计大小
     * @return 共计大小
     */
    long getSize();

    /**
     * 获取种子不可逆匿名识别符
     *
     * @return 不可逆匿名识别符
     */
    default String getHashedIdentifier() {
        String salt = Hashing.crc32().hashString(getHash().toLowerCase(Locale.ROOT), StandardCharsets.UTF_8).toString();
        return Hashing.sha256().hashString(getHash().toLowerCase(Locale.ROOT) + salt, StandardCharsets.UTF_8).toString();
    }

}
