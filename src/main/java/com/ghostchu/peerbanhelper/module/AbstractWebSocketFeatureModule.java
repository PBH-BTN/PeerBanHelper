package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.websocket.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

public abstract class AbstractWebSocketFeatureModule extends AbstractFeatureModule {
    protected final List<WsContext> wsSessions = Collections.synchronizedList(new ArrayList<>());

    public void beforeUpgrade(Context ctx) {
        var ip = userIp(ctx);
        var ua = ctx.userAgent();
        if (!javalinWebContainer.allowAttemptLogin(ip, ua)) {
            ctx.json(new StdResp(false, "Too many failed retries, IP banned.", null));
            throw new UnauthorizedResponse();
        }
        var authResult = javalinWebContainer.isContextAuthorized(ctx);
        if (authResult == JavalinWebContainer.TokenAuthResult.NO_AUTH_TOKEN_PROVIDED) {
            ctx.json(new StdResp(false, tlUI(Lang.WS_LOGS_STREAM_ACCESS_DENIED), null));
            throw new UnauthorizedResponse();
        }
        if (authResult == JavalinWebContainer.TokenAuthResult.FAILED) {
            ctx.json(new StdResp(false, tlUI(Lang.WS_LOGS_STREAM_ACCESS_DENIED), null));
            javalinWebContainer.markLoginFailed(ip, ua);
            throw new UnauthorizedResponse();
        }
        javalinWebContainer.markLoginSuccess(ip, ua, false);
    }

    public void acceptWebSocket(WsConfig wsConfig, Consumer<WsContext> loggedInCallback) {
        wsConfig.onConnect(ctx -> {
            ctx.enableAutomaticPings(15, TimeUnit.SECONDS);
            this.wsSessions.add(ctx);

            wsConfig.onMessage(ctx2 -> {
                if ("PING".equalsIgnoreCase(ctx2.message())) {
                    ctx2.send("PONG");
                }
            });
            wsConfig.onClose(this::revokeWebSocketSession);
            wsConfig.onError(this::revokeWebSocketSession);
            loggedInCallback.accept(ctx);
        });

    }

    private void revokeWebSocketSession(WsErrorContext wsErrorContext) {
        this.wsSessions.remove(wsErrorContext);
        wsErrorContext.closeSession(WsCloseStatus.SERVER_ERROR, JsonUtil.standard().toJson(new StdResp(false, "Internal Server Error, Connection must be closed, check the console for details", null)));
    }

    private void revokeWebSocketSession(WsCloseContext wsCloseContext) {
        this.wsSessions.remove(wsCloseContext);
    }
}
