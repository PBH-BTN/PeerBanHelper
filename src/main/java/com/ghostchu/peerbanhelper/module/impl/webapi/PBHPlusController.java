package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.pbhplus.ActivationKeyManager;
import com.ghostchu.peerbanhelper.pbhplus.ActivationManager;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@IgnoreScan
public final class PBHPlusController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private ActivationManager activationManager;
    @Autowired
    private ActivationKeyManager activationKeyManager;

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
                .get("/api/pbhplus/status", this::handle, Role.USER_READ)
                .put("/api/pbhplus/key", this::handleLicensePut, Role.USER_WRITE)
                .post("/api/pbhplus/renewFreeLicense", this::handleLicenseRenew, Role.USER_WRITE);
    }

    private void handleLicenseRenew(@NotNull Context ctx) throws Exception {
        if (activationManager.isActivated()) {
            ctx.status(HttpStatus.CONFLICT);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.FREE_LICENSE_RENEW_STILL_ACTIVE), null));
            return;
        }
        var newLocalLicense = activationKeyManager.generateLocalLicense();
        Main.getMainConfig().set("pbh-plus-key", newLocalLicense);
        Main.getMainConfig().save(Main.getMainConfigFile());
        activationManager.load();
        if (activationManager.isActivated()) {
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.PBH_PLUS_LICENSE_UPDATED), null));
        } else {
            var keyData = activationManager.getKeyData();
            if (keyData == null) {
                ctx.json(new StdResp(false, tl(locale(ctx), Lang.PBH_PLUS_LICENSE_INVALID), null));
            } else if (System.currentTimeMillis() >= keyData.getExpireAt()) {
                ctx.json(new StdResp(false, tl(locale(ctx), Lang.PBH_PLUS_LICENSE_EXPIRED), null));
            } else {
                ctx.json(new StdResp(false, tl(locale(ctx), Lang.PBH_PLUS_LICENSE_INVALID), null));
            }
        }
    }

    private void handleLicensePut(Context ctx) throws IOException {
        var licenseReq = ctx.bodyAsClass(LicensePutRequest.class);
        Main.getMainConfig().set("pbh-plus-key", licenseReq.key());
        Main.getMainConfig().save(Main.getMainConfigFile());
        activationManager.load();
        if (activationManager.isActivated()) {
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.PBH_PLUS_LICENSE_UPDATED), null));
        } else {
            var keyData = activationManager.getKeyData();
            if (keyData == null) {
                ctx.json(new StdResp(false, tl(locale(ctx), Lang.PBH_PLUS_LICENSE_INVALID), null));
            } else if (System.currentTimeMillis() >= keyData.getExpireAt()) {
                ctx.json(new StdResp(false, tl(locale(ctx), Lang.PBH_PLUS_LICENSE_EXPIRED), null));
            } else {
                ctx.json(new StdResp(false, tl(locale(ctx), Lang.PBH_PLUS_LICENSE_INVALID), null));
            }
        }
    }

    private void handle(Context context) {
        String key;
        if (activationManager.getKeyText().length() > 10) {
            key = activationManager.getKeyText().substring(0, 10) + "******";
        } else {
            key = "**********";
        }
        ActivationKeyManager.KeyData keyData = null;
        ActivationKeyManager.KeyData expiredKeyData = null;
        if(activationManager.getKeyData() != null) {
            if(activationManager.isActivated()){
                keyData = activationManager.getKeyData();
            } else {
                expiredKeyData = activationManager.getKeyData();
            }
        }
        context.json(new StdResp(true, null,
                new ActiveInfo(activationManager.isActivated(),
                        key,keyData, expiredKeyData
                       )));
    }

    @Override
    public void onDisable() {

    }

    public record ActiveInfo(
            boolean activated,
            String key,
            ActivationKeyManager.KeyData keyData,
            ActivationKeyManager.KeyData inactiveKeyData
    ) {

    }

    public record LicensePutRequest(String key) {

    }
}
