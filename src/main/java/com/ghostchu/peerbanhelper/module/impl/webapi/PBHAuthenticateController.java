package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.body.LoginRequestBody;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.SharedObject;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.exception.IPAddressBannedException;
import com.ghostchu.peerbanhelper.web.exception.NeedInitException;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@Slf4j
public final class PBHAuthenticateController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Authenticate";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-authenticate";
    }

    @Override
    public void onEnable() {
        webContainer.routes()
                .post("/api/auth/login", this::handleLogin, Role.ANYONE)
                .post("/api/auth/logout", this::handleLogout, Role.ANYONE);
    }

    @OpenApi(
            path = "/api/auth/logout",
            methods = HttpMethod.POST,
            summary = "注销登录态",
            description = "注销当前会话的登录态",
            tags = {"鉴权"},
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "logout"
    )
    private void handleLogout(Context ctx) {
        ctx.sessionAttribute("authenticated", null);
        ctx.json(new StdResp(true, "success", null));
    }

    @OpenApi(
            path = "/api/auth/login",
            methods = HttpMethod.POST,
            summary = "登录",
            description = "使用访问令牌登录 WebUI",
            tags = {"鉴权"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LoginRequestBody.class)),
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "401", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "login"
    )
    private void handleLogin(Context ctx) throws NeedInitException, IPAddressBannedException {
//        if (webContainer.getToken() == null || webContainer.getToken().isBlank()) {
//            ctx.status(HttpStatus.OK);
//            ctx.json(Map.of("message", "Don't cry okay? Here is dummy success response, let's show OOBE page, it's fine?"));
//            return;
//        }
        if (webContainer.getToken() == null || webContainer.getToken().isBlank()) {
            throw new NeedInitException();
        }
        if (!webContainer.allowAttemptLogin(userIp(ctx), ctx.userAgent())) {
            throw new IPAddressBannedException();
        }
        if (!ExternalSwitch.parseBoolean("pbh.web.requireLogin", true)) {
            webContainer.markLoginSuccess(userIp(ctx), ctx.userAgent(), false);
            ctx.sessionAttribute("authenticated", webContainer.getToken());
            ctx.json(new StdResp(true, "DEBUG: Skipping WebUI login", null));
            return;
        }
        LoginRequestBody loginRequestBody = ctx.bodyAsClass(LoginRequestBody.class);
        if (loginRequestBody == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.WEBAPI_AUTH_INVALID_TOKEN), null));
            return;
        }
        if ("".equalsIgnoreCase(loginRequestBody.getToken())) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.WEBAPI_AUTH_INVALID_TOKEN), null));
            return;
        }
        if (!webContainer.getToken().equals(loginRequestBody.getToken())) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.WEBAPI_AUTH_INVALID_TOKEN), null));
            webContainer.markLoginFailed(userIp(ctx), ctx.userAgent());
            return;
        }
        var silentLoginSecret = ctx.queryParam("silentLogin");
        webContainer.markLoginSuccess(userIp(ctx), ctx.userAgent(), SharedObject.SILENT_LOGIN_TOKEN_FOR_GUI.equals(silentLoginSecret));
        ctx.sessionAttribute("authenticated", webContainer.getToken());
        ctx.json(new StdResp(true, tl(locale(ctx), Lang.WEBAPI_AUTH_OK), null));
    }


    @Override
    public void onDisable() {

    }

}
