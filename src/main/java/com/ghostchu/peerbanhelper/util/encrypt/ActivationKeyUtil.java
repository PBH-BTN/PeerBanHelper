package com.ghostchu.peerbanhelper.util.encrypt;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Cipher;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

// https://blog.csdn.net/silangfeilang/article/details/108403723
public class ActivationKeyUtil {
    public static String PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----\n" +
            "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAPjxqbU5my+HbNXnVRQ7wmnJVE+a/QjG\n" +
            "LmS75Qluo9xWv3JUbH8922JRRPa2rfQWGXJI1GmPK8tpHsrbA02FHkMCAwEAAQ==\n" +
            "-----END PUBLIC KEY-----";

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

    public static String privateKeyEncrypt(String str, String privateKey) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(privateKey);
        PrivateKey priKey = KeyFactory.getInstance("RSA").
                generatePrivate(new PKCS8EncodedKeySpec(decoded));
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, priKey);
        return Base64.getEncoder().encodeToString(cipher.doFinal(str.getBytes()));
    }

    public static String publicKeyDecrypt(String str, String publicKey) throws Exception {
        byte[] inputByte = Base64.getDecoder().decode(str.getBytes(StandardCharsets.UTF_8));
        byte[] decoded = Base64.getDecoder().decode(publicKey);
        PublicKey pubKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(decoded));
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, pubKey);
        return new String(cipher.doFinal(inputByte));
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
            String json = publicKeyDecrypt(key, getPBHPublicKey());
            KeyData keyData = JsonUtil.standard().fromJson(json, KeyData.class);
            if (keyData == null) {
                throw new IllegalStateException("Incorrect key schema");
            }
            if ("PeerBanHelper".equals(keyData.verifyMagic)) {
                return keyData;
            }
            throw new IllegalStateException("Incorrect key: " + json);
        } catch (Exception e) {
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
            Long expireAt
    ) {
    }
}
