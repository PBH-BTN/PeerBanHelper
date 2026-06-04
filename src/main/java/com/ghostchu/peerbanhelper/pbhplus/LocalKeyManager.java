package com.ghostchu.peerbanhelper.pbhplus;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.pbhplus.bean.V2License;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.Pair;
import com.ghostchu.peerbanhelper.util.TimeUtil;
import com.ghostchu.peerbanhelper.util.encrypt.RSAUtils;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public class LocalKeyManager {
    private final String hardwareUUIDHash;
    private Pair<PrivateKey, PublicKey> localKeyPair = null;

    public LocalKeyManager() {
        this.hardwareUUIDHash = Hashing.sha256().hashString(MiscUtil.getHardwareUUID(), StandardCharsets.UTF_8).toString().substring(0, 10);
        try {
            this.localKeyPair = loadLocalKeyPair();
        } catch (Exception e) {
            log.debug("Unable to load local key-pair", e);
        }
    }

    @NotNull
    public Optional<Pair<PrivateKey, PublicKey>> getLocalKeyPair() {
        return Optional.ofNullable(this.localKeyPair);
    }

    public String generateLocalLicense() throws Exception {
        var key = new V2License("",
                "PeerBanHelper",
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
                LocalDateTime.now().plusDays(15).atOffset(TimeUtil.getSystemZoneOffset()).toInstant().toEpochMilli(),
                tlUI(Lang.FREE_LICENSE_DESCRIPTION),
                "Local License",
                List.of("basic"));
        var encrypted = (RSAUtils.encryptByPrivateKey(JsonUtil.standard().toJson(key).getBytes(StandardCharsets.UTF_8), localKeyPair.getKey().getEncoded()));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    private Pair<PrivateKey, PublicKey> loadLocalKeyPair() throws Exception {
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

        return new Pair<>(privateKey, publicKey);
    }
}
