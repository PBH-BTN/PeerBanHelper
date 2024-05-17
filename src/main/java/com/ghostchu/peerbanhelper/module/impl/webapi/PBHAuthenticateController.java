package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.web.Role;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class PBHAuthenticateController extends AbstractFeatureModule {
    private static final Logger log = LoggerFactory.getLogger(PBHAuthenticateController.class);

    public PBHAuthenticateController(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

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
        getServer().getWebContainer().javalin()
                .post("/api/auth/login", this::handleLogin, Role.ANYONE)
                .post("/api/auth/logout", this::handleLogout, Role.ANYONE);
    }

    private void handleLogout(Context ctx) {
        ctx.sessionAttribute("authenticated", null);
        ctx.json(Map.of("message", "success"));
    }

    private void handleLogin(Context ctx) {
        LoginRequest loginRequest = ctx.bodyAsClass(LoginRequest.class);
        if (loginRequest == null || !getServer().getWebContainer().getToken().equals(loginRequest.getToken())) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            ctx.json(Map.of("message", "Incorrect token"));
            return;
        }
        ctx.sessionAttribute("authenticated", getServer().getWebContainer().getToken());
        ctx.status(HttpStatus.OK);
        ctx.json(Map.of("message", "Login success!"));
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
