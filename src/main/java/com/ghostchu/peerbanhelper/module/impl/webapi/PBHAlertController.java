package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.databasent.service.AlertService;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.AlertDTO;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import io.javalin.openapi.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
public final class PBHAlertController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private AlertService alertDao;

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
        webContainer.routes()
                .get("/api/alerts", this::handleListing, Role.USER_READ)
                .patch("/api/alert/{id}/dismiss", this::handleRead, Role.USER_WRITE)
                .post("/api/alert/dismissAll", this::handleAllRead, Role.USER_WRITE)
                .delete("/api/alert/{id}", this::handleDelete, Role.USER_WRITE);
    }

    @OpenApi(
            path = "/api/alert/dismissAll",
            methods = HttpMethod.POST,
            summary = "标记所有告警为已读",
            description = "将所有未读告警标记为已读状态",
            tags = {"告警管理"},
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleAllRead"
    )
    private void handleAllRead(Context context) {
        alertDao.markAllAsRead();
        context.json(new StdResp(true, "OK!", null));
    }


    @OpenApi(
            path = "/api/alerts",
            methods = HttpMethod.GET,
            summary = "获取告警列表",
            description = "获取当前未读告警列表",
            tags = {"告警管理"},
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleListing"
    )
    private void handleListing(Context ctx) {
        ctx.status(200);
        ctx.json(new StdResp(true, null, alertDao.getUnreadAlerts()
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
    }

    @OpenApi(
            path = "/api/alert/{id}/dismiss",
            methods = HttpMethod.PATCH,
            summary = "标记告警为已读",
            description = "根据告警 ID 将指定告警标记为已读",
            tags = {"告警管理"},
            pathParams = {
                    @OpenApiParam(name = "id", description = "告警 ID", required = true)
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "404", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleRead"
    )
    private void handleRead(Context ctx) {
        var id = Long.parseLong(ctx.pathParam("id"));
        var entity = alertDao.getById(id);
        if (entity == null) {
            ctx.status(404);
            ctx.json(new StdResp(false, "Alert not exists or expired", null));
            return;
        }
        entity.setReadAt(OffsetDateTime.now());
        alertDao.saveOrUpdate(entity);
        ctx.status(200);
        ctx.json(new StdResp(true, "OK", null));
    }

    @OpenApi(
            path = "/api/alert/{id}",
            methods = HttpMethod.DELETE,
            summary = "删除告警",
            description = "根据告警 ID 删除指定告警",
            tags = {"告警管理"},
            pathParams = {
                    @OpenApiParam(name = "id", description = "告警 ID", required = true)
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleDelete"
    )
    private void handleDelete(Context ctx) {
        alertDao.removeById(Long.parseLong(ctx.pathParam("id")));
        ctx.status(200);
        ctx.json(new StdResp(true, "OK", null));
    }


    @Override
    public void onDisable() {

    }
}