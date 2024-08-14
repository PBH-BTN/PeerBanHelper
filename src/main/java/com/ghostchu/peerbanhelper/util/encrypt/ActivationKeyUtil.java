package com.ghostchu.peerbanhelper.util.encrypt;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

// https://blog.csdn.net/silangfeilang/article/details/108403723
public class ActivationKeyUtil {
    public static String PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCHxgRTk+Zx/pkN8rpK+Lbr1/f1meapIRDJIgBiSfFy4xdbmDF8wE9PJhdM+3peThz9dJQlt6dkeduIVp65rGS9oZdj7gO5YKtUCDir4NgGQGe1p2C41Xv6RiOXObLmF+ubAJILsimwtDyJT8IysEh9hgaZWnvRXT8JX9wB0Ti2rwIDAQAB";
    /**
     * 获取 PBH PublicKey 用来解密密文，获取 Key 内容
     * 如果 data/REPLACEMENT_PBH_PUBLIC_KEY.pem 文件存在，那么就用这个文件里的公钥替换内置的 KEY
     * 什么？你觉得这很容易被破解？这就对了！
     *
     * @return PBH PublicKey
     */
    public static String getPBHPublicKey() {
        File file = new File(Main.getDataDirectory(), "REPLACEMENT_PBH_PUBLIC_KEY.pem");
        if (file.exists()) {
            try {
                PUBLIC_KEY = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }
        }
        return PUBLIC_KEY;
    }


    /**
     * 从密文获取 KeyData
     *
     * @param key 密文
     * @return KeyData （如果有效），否则 null
     */
    @Nullable
    public static KeyData fromKey(String key) {
        try {
            byte[] encrypted = Base64.getDecoder().decode(key);
            String json = new String(RSAUtils.decryptByPublicKey(encrypted,getPBHPublicKey()),StandardCharsets.UTF_8);
            KeyData keyData = JsonUtil.standard().fromJson(json, KeyData.class);
            if (keyData == null) {
                throw new IllegalStateException("Incorrect key schema");
            }
            if ("PeerBanHelper".equals(keyData.verifyMagic)) {
                return keyData;
            }
            throw new IllegalStateException("Incorrect key: " + json);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public record KeyData(
            // verifyMagic 应固定为 PeerBanHelper
            String verifyMagic,
            // source 为来源
            String source,
            // 授权给（用户名），爱发电的话大概都是 “爱发电用户” 固定的
            String licenseTo,
            // Key 创建时间
            Long createAt,
            // Key 过期时间，通常是 100 年以后
            Long expireAt,
            // 许可证描述
            @Nullable
            String description,
            // 隐藏字段，主要是为了改变 KEY，PBH 并不关心这个字段
            @Nullable
            String hidden
    ) {
    }
}
