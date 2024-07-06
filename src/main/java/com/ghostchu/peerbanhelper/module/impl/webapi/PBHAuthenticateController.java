package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class PBHAuthenticateController extends AbstractFeatureModule {
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
        webContainer.javalin()
                .post("/api/auth/login", this::handleLogin, Role.ANYONE)
                .post("/api/auth/logout", this::handleLogout, Role.ANYONE);
    }

    private void handleLogout(Context ctx) {
        ctx.sessionAttribute("authenticated", null);
        ctx.json(Map.of("message", "success"));
    }

    private void handleLogin(Context ctx) {
        LoginRequest loginRequest = ctx.bodyAsClass(LoginRequest.class);
        if (loginRequest == null || !webContainer.getToken().equals(loginRequest.getToken())) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            ctx.json(Map.of("message", Lang.WEBAPI_AUTH_INVALID_TOKEN));
            return;
        }
        ctx.sessionAttribute("authenticated", webContainer.getToken());
        ctx.status(HttpStatus.OK);
        ctx.json(Map.of("message", Lang.WEBAPI_AUTH_OK));
    }


    @Override
    public void onDisable() {

    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    static class LoginRequest {
        private String token;
    }
}
