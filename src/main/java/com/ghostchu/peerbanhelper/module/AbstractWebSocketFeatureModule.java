package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.websocket.*;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

public abstract class AbstractWebSocketFeatureModule extends AbstractFeatureModule {
    protected final List<WsContext> wsSessions = Collections.synchronizedList(new ArrayList<>());

    public void acceptWebSocket(WsConfig wsConfig, Consumer<WsContext> loggedInCallback) {
        wsConfig.onConnect(ctx -> {
            if (ctx.session.getRemoteAddress() instanceof InetSocketAddress inetSocketAddress) {
                if (!javalinWebContainer.allowAttemptLogin(inetSocketAddress.getHostString(), ctx.getUpgradeCtx$javalin().userAgent())) {
                    ctx.closeSession(WsCloseStatus.TRY_AGAIN_LATER, JsonUtil.standard().toJson(new StdResp(false, "Too many failed retries, IP banned.", null)));
                    return;
                }
            }
            var authResult = javalinWebContainer.isContextAuthorized(ctx.getUpgradeCtx$javalin());
            if (authResult == JavalinWebContainer.TokenAuthResult.NO_AUTH_TOKEN_PROVIDED) {
                ctx.closeSession(WsCloseStatus.POLICY_VIOLATION, JsonUtil.standard().toJson(new StdResp(false, tlUI(Lang.WS_LOGS_STREAM_ACCESS_DENIED), null)));
                return;
            }
            if (authResult == JavalinWebContainer.TokenAuthResult.FAILED) {
                ctx.closeSession(WsCloseStatus.POLICY_VIOLATION, JsonUtil.standard().toJson(new StdResp(false, tlUI(Lang.WS_LOGS_STREAM_ACCESS_DENIED), null)));
                javalinWebContainer.markLoginFailed(userIp(ctx.getUpgradeCtx$javalin()), ctx.getUpgradeCtx$javalin().userAgent());
                return;
            }
            javalinWebContainer.markLoginSuccess(userIp(ctx.getUpgradeCtx$javalin()), ctx.getUpgradeCtx$javalin().userAgent(), false);
            ctx.enableAutomaticPings(15, TimeUnit.SECONDS);
            this.wsSessions.add(ctx);

            wsConfig.onMessage(ctx2 -> {
                if (ctx2.message().equalsIgnoreCase("PING")) {
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
