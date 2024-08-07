package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.pbhplus.ActivationManager;
import com.ghostchu.peerbanhelper.util.encrypt.ActivationKeyUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
        webContainer.javalin().get("/api/pbhplus/status", this::handle, Role.USER_READ);
    }

    private void handle(Context context) {
        context.json(new StdResp(true,null,new ActiveInfo(activationManager.isActivated(), activationManager.getKeyText(), activationManager.getKeyData())));
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
}
