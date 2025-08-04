package com.ghostchu.peerbanhelper.pbhplus;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.pbhplus.bean.License;
import com.ghostchu.peerbanhelper.pbhplus.bean.V1License;
import com.ghostchu.peerbanhelper.pbhplus.bean.V2License;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.encrypt.RSAUtils;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
public class LicenseParser {
    public static final String OFFICIAL_PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCHxgRTk+Zx/pkN8rpK+Lbr1/f1meapIRDJIgBiSfFy4xdbmDF8wE9PJhdM+3peThz9dJQlt6dkeduIVp65rGS9oZdj7gO5YKtUCDir4NgGQGe1p2C41Xv6RiOXObLmF+ubAJILsimwtDyJT8IysEh9hgaZWnvRXT8JX9wB0Ti2rwIDAQAB";
    private static final String hardwareUUIDHash;
    private final Map.Entry<PrivateKey, PublicKey> localKeyPair;

    static {
        SystemInfo systemInfo = new SystemInfo();
        String hardwareUUID = systemInfo.getHardware().getComputerSystem().getHardwareUUID();
        hardwareUUIDHash = Hashing.sha256().hashString(hardwareUUID, StandardCharsets.UTF_8).toString().substring(0, 10);
    }

    public LicenseParser() throws Exception {
        localKeyPair = loadLocalKeyPair();
    }

    @NotNull
    public License fromLicense(String encryptedLicense) throws IllegalArgumentException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, InvalidKeySpecException, BadPaddingException, IOException, InvalidKeyException {
        byte[] encrypted = Base64.getDecoder().decode(encryptedLicense);
        String json = new String(RSAUtils.decryptByPublicKey(encrypted, OFFICIAL_PUBLIC_KEY), StandardCharsets.UTF_8);
        JsonElement parser = JsonParser.parseString(json);
        if (!parser.isJsonObject())
            throw new IllegalArgumentException("License data is not a valid JSON object");
        var licenseObject = parser.getAsJsonObject();
        var verifyMagicObj = licenseObject.get("verifyMagic");
        if (verifyMagicObj == null || !verifyMagicObj.isJsonPrimitive())
            throw new IllegalArgumentException("License data is missing 'verifyMagic' field or it is not a string");
        if (!"PeerBanHelper".equals(verifyMagicObj.getAsString()))
            throw new IllegalArgumentException("Incorrect verify magic: excepted->PeerBanHelper, actual->" + verifyMagicObj);
        var version = licenseObject.get("licenseVersion");
        if (version == null)
            return fromLicenseV1(json);
        else if (version.getAsInt() == 2)
            return fromLicenseV2(json);
        throw new IllegalArgumentException("Unsupported license version: " + version.getAsInt() + ", Try update PeerBanHelper.");
    }

    private @NotNull License fromLicenseV2(@NotNull String json) {
        return JsonUtil.standard().fromJson(json, V2License.class);
    }

    private @NotNull License fromLicenseV1(@NotNull String json) {
        return JsonUtil.standard().fromJson(json, V1License.class);
    }

    public String generateLocalLicense() throws Exception {
        var key = new V2License("PeerBanHelper",
                2,
                "local",
                tlUI(Lang.FREE_LICENSE_SOURCE),
                System.getProperty("user.name", "Local User"),
                null,
                null,
                null,
                null,
                null,
                BigDecimal.ZERO,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                LocalDateTime.now().plusDays(15).atOffset(ZoneOffset.UTC).toInstant().toEpochMilli(),
                tlUI(Lang.FREE_LICENSE_DESCRIPTION),
                "Local License",
                List.of("basic"));
        var encrypted = (RSAUtils.encryptByPrivateKey(new Gson().toJson(key).getBytes(StandardCharsets.UTF_8),
                Base64.getEncoder().encodeToString(getLocalKeyPair().getKey().getEncoded())));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public Map.Entry<PrivateKey, PublicKey> getLocalKeyPair() {
        return localKeyPair;
    }

    private Map.Entry<PrivateKey, PublicKey> loadLocalKeyPair() throws Exception {
        File publicKeyFile = new File(Main.getDataDirectory(), "local_license2_keypair_" + hardwareUUIDHash + ".pub");
        File privateKeyFile = new File(Main.getDataDirectory(), "local_license2_keypair_" + hardwareUUIDHash + ".key");

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
}
