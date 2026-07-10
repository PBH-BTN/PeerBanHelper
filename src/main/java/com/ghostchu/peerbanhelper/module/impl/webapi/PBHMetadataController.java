package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.BuildMeta;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.ModuleManagerImpl;
import com.ghostchu.peerbanhelper.module.ModuleStatusType;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.ModuleRecordDTO;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import io.javalin.openapi.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public final class PBHMetadataController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private BuildMeta buildMeta;
    @Autowired
    private ModuleManagerImpl moduleManager;

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Metadata";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-metadata";
    }

    @Override
    public void onEnable() {
        webContainer.routes().get("/api/metadata/manifest", this::handleManifest, Role.ANYONE);
    }

    @OpenApi(
            path = "/api/metadata/manifest",
            methods = HttpMethod.GET,
            summary = "请求基本清单数据",
            description = "返回版本、模块和安装信息",
            tags = {"元数据"},
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleManifest"
    )
    private void handleManifest(Context ctx) {
        Map<String, Object> data = new HashMap<>();
        data.put("version", buildMeta);
        //if (webContainer.isContextAuthorized(ctx) == JavalinWebContainer.TokenAuthResult.SUCCESS) {
        data.put("modules", moduleManager.getModules().stream()
                .filter(module -> module.getModuleStatus().getType() == ModuleStatusType.ENABLED)
                .map(f -> new ModuleRecordDTO(f.getClass().getName(), f.getConfigName())).toList());
        // } else {
        //     data.put("modules", Collections.emptyList());
        // }
        data.put("installationId", Main.getMainConfig().getString("installation-id", "not-initialized"));
        data.put("analytics", Main.getMainConfig().getBoolean("privacy.analytics"));
        ctx.json(new StdResp(true, null, data));
    }

    @Override
    public void onDisable() {

    }

}
