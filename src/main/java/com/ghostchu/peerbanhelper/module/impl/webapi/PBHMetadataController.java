package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.BuildMeta;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.FeatureModule;
import com.ghostchu.peerbanhelper.module.ModuleManager;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PBHMetadataController extends AbstractFeatureModule {
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
        return "WebAPI - Metadata";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-metadata";
    }

    @Override
    public void onEnable() {
        webContainer.javalin().get("/api/metadata/manifest", this::handleManifest, Role.ANYONE);
    }

    private void handleManifest(Context ctx) {
        Map<String, Object> data = new HashMap<>();
        data.put("version", buildMeta);
        data.put("modules", moduleManager.getModules().stream()
                .filter(FeatureModule::isModuleEnabled)
                .map(f -> new ModuleRecord(f.getClass().getName(), f.getConfigName())).toList());
        ctx.json(new StdResp(true,null,data));
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
