package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.web.Role;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class PBHMetadataController extends AbstractFeatureModule {
    public PBHMetadataController(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

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
        getServer().getWebContainer().javalin().get("/api/metadata/manifest", this::handleManifest, Role.ANYONE);
    }

    private void handleManifest(Context ctx) {
        ctx.status(HttpStatus.OK);
        Map<String, Object> data = new HashMap<>();
        data.put("version", Main.getMeta());
        data.put("modules", getServer().getModuleManager().getModules().stream().map(f -> new ModuleRecord(f.getClass().getName(), f.getConfigName())).toList());
        ctx.json(data);
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
