package com.ghostchu.peerbanhelper.pbhplus;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.encrypt.RSAUtils;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
public final class ActivationKeyManager {
    public static String OFFICIAL_PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCHxgRTk+Zx/pkN8rpK+Lbr1/f1meapIRDJIgBiSfFy4xdbmDF8wE9PJhdM+3peThz9dJQlt6dkeduIVp65rGS9oZdj7gO5YKtUCDir4NgGQGe1p2C41Xv6RiOXObLmF+ubAJILsimwtDyJT8IysEh9hgaZWnvRXT8JX9wB0Ti2rwIDAQAB";
    private static final String hardwareUUIDHash;

    static {
        SystemInfo systemInfo = new SystemInfo();
        String hardwareUUID = systemInfo.getHardware().getComputerSystem().getHardwareUUID();
        hardwareUUIDHash = Hashing.sha256().hashString(hardwareUUID, StandardCharsets.UTF_8).toString().substring(0, 10);
    }

    private final Map.Entry<PrivateKey, PublicKey> localKeyPair;

    public ActivationKeyManager() throws Exception {
        localKeyPair = loadLocalKeyPair();
    }

    /**
     * 从密文获取 KeyData
     *
     * @param key 密文
     * @return KeyData （如果有效），否则 null
     */
    @Nullable
    public KeyData fromKey(String pubKey, String key) {
        try {
            byte[] encrypted = Base64.getDecoder().decode(key);
            String json = new String(RSAUtils.decryptByPublicKey(encrypted, pubKey), StandardCharsets.UTF_8);
            KeyData keyData = JsonUtil.standard().fromJson(json, KeyData.class);
            if (keyData == null) {
                throw new IllegalStateException("Incorrect key schema");
            }
            String description = keyData.description;
            if (description != null) {
                if (description.equalsIgnoreCase("No description")
                    || description.isBlank()) {
                    description = null;
                }
            }
            keyData.setDescription(description);
            if ("PeerBanHelper".equals(keyData.verifyMagic)) {
                return keyData;
            }
            throw new IllegalStateException("Incorrect key: " + json);
        } catch (Exception e) {
            return null;
        }
    }

    public String generateLocalLicense() throws Exception {
        var key = new KeyData("PeerBanHelper",
                tlUI(Lang.FREE_LICENSE_SOURCE),
                System.getProperty("user.name", "Local User"),
                System.currentTimeMillis(),
                LocalDateTime.now().plusDays(15).atOffset(ZoneOffset.UTC).toInstant().toEpochMilli(),
                tlUI(Lang.FREE_LICENSE_DESCRIPTION),
                "Local License", "local");
        var encrypted = (RSAUtils.encryptByPrivateKey(new Gson().toJson(key).getBytes(StandardCharsets.UTF_8),
                Base64.getEncoder().encodeToString(getLocalKeyPair().getKey().getEncoded())));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public Map.Entry<PrivateKey, PublicKey> getLocalKeyPair() {
        return localKeyPair;
    }

    private Map.Entry<PrivateKey, PublicKey> loadLocalKeyPair() throws Exception {
        File publicKeyFile = new File(Main.getDataDirectory(), "local_license_keypair_" + hardwareUUIDHash + ".pub");
        File privateKeyFile = new File(Main.getDataDirectory(), "local_license_keypair_" + hardwareUUIDHash + ".key");

        if (!publicKeyFile.exists() || !privateKeyFile.exists()) {
            var map = RSAUtils.genKeyPair();
            var privateKeyObj = (RSAPrivateKey) map.get(RSAUtils.PRIVATE_KEY);
            var publicKeyObj = (RSAPublicKey) map.get(RSAUtils.PUBLIC_KEY);
            Files.write(privateKeyFile.toPath(), privateKeyObj.getEncoded());
            Files.write(publicKeyFile.toPath(), publicKeyObj.getEncoded());
        }
        // read to RSAPrivateKey
        byte[] privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath());
        byte[] publicKeyBytes = Files.readAllBytes(publicKeyFile.toPath());

        PrivateKey privateKey = RSAUtils.getRSAPrivateKeyFromRawEncoded(privateKeyBytes);
        PublicKey publicKey = RSAUtils.getRSAPublicKeyFromRawEncoded(publicKeyBytes);

        return Map.entry(privateKey, publicKey);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyData {
        // verifyMagic 应固定为 PeerBanHelper
        private String verifyMagic;
        // source 为来源
        private String source;
        // 授权给（用户名）
        private String licenseTo;
        // Key 创建时间
        private Long createAt;
        // Key 过期时间，通常是 100 年以后
        private Long expireAt;
        // 许可证描述
        @Nullable
        private String description;
        // 隐藏字段，主要是为了改变 KEY，PBH 并不关心这个字段
        @Nullable
        private String hidden;
        @Nullable
        private String type = "afdian"; // 默认字段
    }

}
