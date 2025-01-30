package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.database.dao.impl.AlertDao;
import com.ghostchu.peerbanhelper.database.table.AlertEntity;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.util.paging.Page;
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

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@IgnoreScan
public final class PBHAlertController extends AbstractFeatureModule {
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
                .patch("/api/alert/{id}/dismiss", this::handleRead, Role.USER_WRITE)
                .post("/api/alert/dismissAll", this::handleAllRead, Role.USER_WRITE)
                .delete("/api/alert/{id}", this::handleDelete, Role.USER_WRITE);
    }

    private void handleAllRead(Context context) throws SQLException {
        alertDao.markAllAsRead();
        context.json(new StdResp(true, "OK!", null));
    }


    private void handleListing(Context ctx) throws SQLException {
        ctx.status(200);
        if (ctx.queryParam("unread") != null && Boolean.parseBoolean(ctx.queryParam("unread"))) {
            ctx.json(new StdResp(true, null, alertDao.getUnreadAlertsUnPaged()
                    .stream()
                    .map(alert -> new AlertDTO(
                            alert.getId(),
                            alert.getCreateAt(),
                            alert.getReadAt(),
                            alert.getLevel(),
                            alert.getIdentifier(),
                            tl(locale(ctx), alert.getTitle()),
                            tl(locale(ctx), alert.getContent())
                    ))
                    .toList()
            ));
        } else {
            Page<AlertEntity> alerts = alertDao.queryByPaging(new Pageable(ctx));
            var newAlerts = new Page<>(alerts.getPage(), alerts.getSize(), alerts.getTotal(),
                    alerts.getResults().stream().map(alert -> new AlertDTO(
                            alert.getId(),
                            alert.getCreateAt(),
                            alert.getReadAt(),
                            alert.getLevel(),
                            alert.getIdentifier(),
                            tl(locale(ctx), alert.getTitle()),
                            tl(locale(ctx), alert.getContent())
                    )).toList());
            ctx.json(new StdResp(true, null, newAlerts));
        }
    }

    private void handleRead(Context ctx) throws SQLException {
        var id = Long.parseLong(ctx.pathParam("id"));
        var entity = alertDao.queryForId(id);
        entity.setReadAt(new Timestamp(System.currentTimeMillis()));
        alertDao.update(entity);
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

    record AlertDTO(Long id, Timestamp createAt, Timestamp readAt, AlertLevel level, String identifier, String title,
                    String content) {

    }
}