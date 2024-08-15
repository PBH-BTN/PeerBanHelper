package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.pbhplus.ActivationManager;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.encrypt.ActivationKeyUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
public class PBHPlusController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private ActivationManager activationManager;

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
                .put("/api/pbhplus/key", this::handleLicensePut, Role.USER_WRITE);
    }

    private void handleLicensePut(Context ctx) throws IOException {
        var licenseReq = ctx.bodyAsClass(LicensePutRequest.class);
        Main.getMainConfig().set("pbh-plus-key", licenseReq.key());
        Main.getMainConfig().save(Main.getMainConfigFile());
        activationManager.load();
        ctx.json(new StdResp(true, tl(locale(ctx), Lang.PBH_PLUS_LICENSE_UPDATE), null));
    }

    private void handle(Context context) {
        String key = null;
        if (activationManager.getKeyText().length() > 10) {
            key = activationManager.getKeyText().substring(0, 10) + "******";
        } else {
            key = activationManager.getKeyText().substring(0, 5) + "***********";
        }
        context.json(new StdResp(true, null,
                new ActiveInfo(activationManager.isActivated(),
                        key,
                        activationManager.getKeyData())));
    }

    @Override
    public void onDisable() {

    }

    public record ActiveInfo(
            boolean activated,
            String key,
            ActivationKeyUtil.KeyData keyData
    ) {

    }

    public record LicensePutRequest(String key) {

    }
}
