package com.ghostchu.peerbanhelper.util;

import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class InfoHashUtil {
    public static String getHashedIdentifier(String torrentInfoHash) {
        String torrentInfoHandled = torrentInfoHash.toLowerCase(Locale.ROOT); // 转小写处理
        String salt = Hashing.crc32().hashString(torrentInfoHandled, StandardCharsets.UTF_8).toString(); // 使用 crc32 计算 info_hash 的哈希作为盐
        return Hashing.sha256().hashString(torrentInfoHandled + salt, StandardCharsets.UTF_8).toString(); // 在 info_hash 的明文后面追加盐后，计算 SHA256 的哈希值，结果应转全小写
    }
}
