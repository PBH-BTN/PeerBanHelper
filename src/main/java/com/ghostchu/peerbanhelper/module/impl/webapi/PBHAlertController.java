package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.web.Role;
import io.javalin.http.Context;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

public class PBHAlertController extends AbstractFeatureModule {
    public PBHAlertController(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Alerts";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-alerts";
    }

    @Override
    public void onEnable() {
        getServer().getWebContainer().javalin().get("/api/alerts", this::handleListing, Role.USER_READ);
        getServer().getWebContainer().javalin().delete("/api/alert/{id}", this::handleDelete, Role.USER_WRITE);
    }

    private void handleListing(Context ctx) {
        ctx.status(200);
        ctx.json(getServer().getAlertManager().getAlerts());
    }

    private void handleDelete(Context ctx) {
        if (getServer().getAlertManager().removeAlert(ctx.pathParam("id"))) {
            ctx.status(204);
        } else {
            ctx.status(404);
        }
    }


    @Override
    public void onDisable() {

    }
}
