package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.NewLogEntryCreatedEvent;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.logger.JListAppender;
import com.ghostchu.peerbanhelper.util.logger.LogEntry;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.google.common.eventbus.Subscribe;
import io.javalin.http.Context;
import io.javalin.websocket.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
@IgnoreScan
public class PBHLogsController extends AbstractFeatureModule {
    private final List<WsContext> session = Collections.synchronizedList(new ArrayList<>());
    @Autowired
    private JavalinWebContainer webContainer;

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Logs";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-logs";
    }

    @Override
    public void onEnable() {
        webContainer.javalin()
                .get("/api/logs/history", this::handleLogs, Role.USER_READ)
                .ws("/api/logs/stream", this::handleLogsStream, Role.USER_READ);
        Main.getEventBus().register(this);
    }


    @Subscribe
    public void onLogRecordCreated(NewLogEntryCreatedEvent event) {
        synchronized (session) {
            for (WsContext wsContext : session) {
                wsContext.send(new StdResp(true, null,
                        new WebSocketLogEntry(
                                event.getEntry().time(),
                                event.getEntry().thread(),
                                event.getEntry().level().name(),
                                event.getEntry().content(),
                                event.getEntry().seq()
                        )));
            }
        }
    }

    private void handleLogsStream(WsConfig wsConfig) {
        wsConfig.onConnect(ctx -> {
            if (ctx.session.getRemoteAddress() instanceof InetSocketAddress inetSocketAddress) {
                if (!webContainer.allowAttemptLogin(inetSocketAddress.getHostString())) {
                    ctx.closeSession(WsCloseStatus.TRY_AGAIN_LATER, JsonUtil.standard().toJson(new StdResp(false, "Too many failed retries, IP banned.", null)));
                    return;
                }
            }
            if (!webContainer.isContextAuthorized(ctx.getUpgradeCtx$javalin())) {
                ctx.closeSession(WsCloseStatus.POLICY_VIOLATION, JsonUtil.standard().toJson(new StdResp(false, tlUI(Lang.WS_LOGS_STREAM_ACCESS_DENIED), null)));
                webContainer.markLoginFailed(userIp(ctx.getUpgradeCtx$javalin()));
                return;
            }
            webContainer.markLoginSuccess(userIp(ctx.getUpgradeCtx$javalin()));
            ctx.enableAutomaticPings(15, TimeUnit.SECONDS);
            this.session.add(ctx);
            var offset = ctx.queryParam("offset");
            sendHistoryLogs(ctx, Long.parseLong(offset == null ? "0" : offset));
        });
        wsConfig.onMessage(ctx -> {
            if (ctx.message().equalsIgnoreCase("PING")) {
                ctx.send("PONG");
                return;
            }
            handleClientMessage(ctx, session, ctx.message());
        });
        wsConfig.onClose(this::revokeWebSocketSession);
        wsConfig.onError(this::revokeWebSocketSession);
    }

    private void sendHistoryLogs(WsContext ctx, long offset) {
        if (offset > JListAppender.getSeq().longValue()) {
            offset = 0; // PBH 重启，但是 WebUI 没有刷新
        }
        for (LogEntry logEntry : JListAppender.ringDeque) {
            if (logEntry.seq() > offset) {
                ctx.send(new StdResp(true, null,
                        new WebSocketLogEntry(
                                logEntry.time(),
                                logEntry.thread(),
                                logEntry.level().name(),
                                logEntry.content(),
                                logEntry.seq()
                        )));
            }
        }
    }

    private void handleClientMessage(WsMessageContext ctx, List<WsContext> session, String message) {

    }

    private void revokeWebSocketSession(WsErrorContext wsErrorContext) {
        this.session.remove(wsErrorContext);
        wsErrorContext.closeSession(WsCloseStatus.SERVER_ERROR, JsonUtil.standard().toJson(new StdResp(false, "Internal Server Error, Connection must be closed, check the console for details", null)));
    }

    private void revokeWebSocketSession(WsCloseContext wsCloseContext) {
        this.session.remove(wsCloseContext);
    }

    private void handleLogs(Context ctx) {
        ctx.status(200);
        var list = JListAppender.ringDeque.stream().map(e -> new WebSocketLogEntry(
                e.time(),
                e.thread(),
                e.level().name(),
                e.content(),
                e.seq()
        )).toList();
        ctx.json(new StdResp(true, null, list));
    }


    @Override
    public void onDisable() {
        Main.getEventBus().unregister(this);
    }

    @AllArgsConstructor
    @Data
    private static class WebSocketLogEntry {
        private long time;
        private String thread;
        private String level;
        private String content;
        private long offset;
    }
}
