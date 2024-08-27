package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.database.dao.impl.AlertDao;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.util.paging.Pageable;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;

@Component
@IgnoreScan
public class PBHAlertController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private AlertDao alertDao;

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
                .post("/api/alert/{id}/markAsRead", this::handleRead, Role.USER_WRITE)
                .delete("/api/alert/{id}", this::handleDelete, Role.USER_WRITE);
    }

    private void handleListing(Context ctx) throws SQLException {
        ctx.status(200);
        ctx.json(new StdResp(true, null, alertDao.queryByPaging(new Pageable(ctx))));
    }

    private void handleRead(Context ctx) throws SQLException {
        var id = Long.parseLong(ctx.pathParam("id"));
        var entity = alertDao.queryForId(id);
        entity.setReadAt(new Timestamp(System.currentTimeMillis()));
        ctx.status(200);
        ctx.json(new StdResp(true, "OK", null));
    }

    private void handleDelete(Context ctx) throws SQLException {
        alertDao.deleteById(Long.parseLong(ctx.pathParam("id")));
        ctx.status(200);
        ctx.json(new StdResp(true, "OK", null));
    }


    @Override
    public void onDisable() {

    }
}