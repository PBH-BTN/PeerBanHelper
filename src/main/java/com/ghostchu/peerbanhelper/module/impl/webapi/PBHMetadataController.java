package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

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
        getServer().getJavalinWebContainer().getJavalin().get("/api/metadata/manifest", this::handleManifest);
    }

    private void handleManifest(Context ctx) {
        ctx.status(HttpStatus.OK);
        ctx.json(Main.getMeta());
    }

    @Override
    public void onDisable() {

    }
}
