package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PBHAlertController extends AbstractFeatureModule {
    @Autowired
    private AlertManager alertManager;
    @Autowired
    private JavalinWebContainer webContainer;

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
        webContainer.javalin()
                .get("/api/alerts", this::handleListing, Role.USER_READ)
                .delete("/api/alert/{id}", this::handleDelete, Role.USER_WRITE);
    }

    private void handleListing(Context ctx) {
        ctx.status(200);
        ctx.json(new StdResp(true, null, alertManager.getAlerts()));
    }

    private void handleDelete(Context ctx) {
        if (alertManager.removeAlert(ctx.pathParam("id"))) {
            ctx.status(204);
        } else {
            ctx.status(404);
        }
    }


    @Override
    public void onDisable() {

    }
}
