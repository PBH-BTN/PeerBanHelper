package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.body.LicensePutRequestBody;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.LicenseKeyPairDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.PBHPlusStatusDTO;
import com.ghostchu.peerbanhelper.pbhplus.LicenseDownloader;
import com.ghostchu.peerbanhelper.pbhplus.LicenseManager;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
public final class PBHPlusController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private LicenseManager licenseManager;
    @Autowired
    private LicenseDownloader licenseDownloader;

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - PBH Plus Interface";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-pbh-plus";
    }

    @Override
    public void onEnable() {
        webContainer.javalin()
                .get("/api/pbhplus/status", this::handleStatus, Role.USER_READ)
                .put("/api/pbhplus/key", this::handleLicensePut, Role.USER_WRITE)
                .delete("/api/pbhplus/key", this::handleLicenseDelete, Role.USER_WRITE)
                .post("/api/pbhplus/renewFreeLicense", this::handleLicenseRenew, Role.USER_WRITE);
    }

    private void handleLicensePut(@NotNull Context context) throws Exception {
        var body = context.bodyAsClass(LicensePutRequestBody.class);
        var input = body.key().trim();
        processLicensePut(context, input, false);
    }

    private void processLicensePut(Context context, String input, boolean selfCall) throws Exception {
        try {
            var license = licenseManager.getLicenseParser().fromLicense(input);
            var status = licenseManager.getLicenseBackend().getLicenseStatus(license);
            switch (status) {
                case EXPIRED -> context.json(new StdResp(false, tlUI(Lang.PBH_LICENSE_KEY_EXPIRED), null));
                case REVOKED -> context.json(new StdResp(false, tlUI(Lang.PBH_LICENSE_KEY_REVOKED), null));
                case VALID, NOT_STARTED -> {
                    var keyList = Main.getMainConfig().getStringList("pbh-plus-key");
                    if (!keyList.contains(input))
                        keyList.add(input);
                    Main.getMainConfig().set("pbh-plus-key", keyList);
                    Main.getMainConfig().save(Main.getMainConfigFile());
                    licenseManager.reloadModule();
                    context.json(new StdResp(true, tlUI(Lang.PBH_PLUS_LICENSE_UPDATED), null));
                }
                default -> context.json(new StdResp(false, tlUI(Lang.PBH_LICENSE_PARSE_FAILED), null));
            }
        } catch (NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException |
                 InvalidKeySpecException | BadPaddingException | IOException | InvalidKeyException e) {
            if (!selfCall) {
                var downloadedLicense = licenseDownloader.attemptDownloadFromServer(input);
                if (downloadedLicense != null) {
                    processLicensePut(context, downloadedLicense, true);
                    return;
                }
            }
            context.json(new StdResp(false, tlUI(Lang.PBH_LICENSE_PARSE_FAILED), null));
        }
    }

    private void handleLicenseRenew(@NotNull Context context) throws Exception {
        var local = licenseManager.getLicenseParser().generateLocalLicense();
        var keyList = Main.getMainConfig().getStringList("pbh-plus-key");
        if (!keyList.contains(local))
            keyList.add(local);
        licenseManager.reloadModule();
        context.json(new StdResp(true, tlUI(Lang.PBH_PLUS_LICENSE_UPDATED), null));
    }

    private void handleLicenseDelete(@NotNull Context context) throws Exception {
        String key = context.bodyAsClass(LicensePutRequestBody.class).key();
        var keyList = Main.getMainConfig().getStringList("pbh-plus-key");
        if (keyList.remove(key.trim())) {
            Main.getMainConfig().set("pbh-plus-key", keyList);
            Main.getMainConfig().save(Main.getMainConfigFile());
            licenseManager.reloadModule();
            context.json(new StdResp(true, tlUI(Lang.PBH_LICENSE_KEY_DELETED), null));
        } else {
            context.json(new StdResp(false, tlUI(Lang.PBH_LICENSE_KEY_DELETE_FAILED_NOT_EXISTS), null));
        }
    }

    private void handleStatus(@NotNull Context context) {
        List<LicenseKeyPairDTO> licenseKeyPairDTOList = new ArrayList<>();
        licenseManager.getLicenseBackend().getLicensesMap().forEach((key, license) -> {
            var dto = new LicenseKeyPairDTO(key, license);
            licenseKeyPairDTOList.add(dto);
        });
        context.json(new StdResp(true, null, new PBHPlusStatusDTO(licenseManager.getLicenseBackend().getAllFeatures(), licenseKeyPairDTOList)));
    }

    @Override
    public void onDisable() {

    }
}
