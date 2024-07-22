package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.BuildMeta;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.ModuleManager;
import com.ghostchu.peerbanhelper.module.impl.webapi.common.SlimMsg;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

/*
   :(
 */
@Component
public class PBHOOBEController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private BuildMeta buildMeta;
    @Autowired
    private ModuleManager moduleManager;

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - OOBE Interface";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-oobe";
    }

    @Override
    public void onEnable() {
        webContainer.javalin().post("/api/oobe", this::handleOOBERequest, Role.ANYONE); // 指定 ANYONE，否则会被鉴权代码拉取鉴权

    }

    private void handleOOBERequest(Context context) {
        if (webContainer.getToken() != null && !webContainer.getToken().isBlank()) {
            context.status(HttpStatus.FORBIDDEN);
            context.json(new SlimMsg(false, tl(locale(context), Lang.OOBE_DISALLOW_REINIT), 403));
            return;
        }
        var jsonReq = context.body();


    }


    @Override
    public void onDisable() {

    }

    record ModuleRecord(
            String className,
            String configName
    ) {
    }
}
