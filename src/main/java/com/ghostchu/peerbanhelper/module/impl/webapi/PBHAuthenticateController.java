package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.exception.IPAddressBannedException;
import com.ghostchu.peerbanhelper.web.exception.NeedInitException;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@Slf4j
@IgnoreScan
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
        ctx.json(new StdResp(true, "success", null));
    }

    private void handleLogin(Context ctx) throws NeedInitException, IPAddressBannedException {
//        if (webContainer.getToken() == null || webContainer.getToken().isBlank()) {
//            ctx.status(HttpStatus.OK);
//            ctx.json(Map.of("message", "Don't cry okay? Here is dummy success response, let's show OOBE page, it's fine?"));
//            return;
//        }
        if (webContainer.getToken() == null || webContainer.getToken().isBlank()) {
            throw new NeedInitException();
        }

        if(!webContainer.allowAttemptLogin(userIp(ctx))){
            throw new IPAddressBannedException();
        }

        LoginRequest loginRequest = ctx.bodyAsClass(LoginRequest.class);
        if(loginRequest == null){
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.WEBAPI_AUTH_INVALID_TOKEN), null));
            return;
        }
        if ( !webContainer.getToken().equals(loginRequest.getToken())) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.WEBAPI_AUTH_INVALID_TOKEN), null));
            webContainer.markLoginFailed(userIp(ctx));
            return;
        }
        webContainer.markLoginSuccess(userIp(ctx));
        ctx.sessionAttribute("authenticated", webContainer.getToken());
        ctx.json(new StdResp(true, tl(locale(ctx), Lang.WEBAPI_AUTH_OK), null));
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
