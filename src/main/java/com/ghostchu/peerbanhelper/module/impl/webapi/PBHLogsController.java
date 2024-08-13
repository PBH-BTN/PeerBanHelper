package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.LoggerEventRecordCreatedEvent;
import com.ghostchu.peerbanhelper.log4j2.MemoryLoggerAppender;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.google.common.eventbus.Subscribe;
import io.javalin.http.Context;
import io.javalin.websocket.WsCloseStatus;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Component
@Slf4j
public class PBHLogsController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    private final Map<WsContext, WebSocketSession> connectedWebSocketSessions = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduledService;

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
        scheduledService = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
        scheduledService.scheduleWithFixedDelay(this::cronJob,0, 1000*15, TimeUnit.MILLISECONDS);
    }

    private void cronJob() {
        try{
            for (Map.Entry<WsContext, WebSocketSession> e : new ArrayList<>(connectedWebSocketSessions.entrySet())) {
                if(!e.getValue().isLoggedIn()){
                    if(System.currentTimeMillis() - e.getValue().getConnectedAt() > 1000*15){
                        e.getKey().closeSession(WsCloseStatus.NORMAL_CLOSURE, "Login timed out");
                    }
                }
            }

        }catch (Throwable throwable){
            log.error("Exception occurred during cron job in PBHLogsController", throwable);
        }
    }

    @Subscribe
    public void onLogRecordCreated(LoggerEventRecordCreatedEvent eventRecordCreatedEvent) {
        connectedWebSocketSessions.entrySet()
                .stream()
                .filter(e -> e.getValue().isLoggedIn())
                .forEach(e -> e.getKey().send(JsonUtil.standard().toJson(
                        new WebSocketServerMessage<>(UUID.randomUUID().toString(), "logEntryPush", eventRecordCreatedEvent.getRecord())
                )));
    }

    private void handleLogsStream(WsConfig wsConfig) {
        wsConfig.onConnect(ctx -> {
            ctx.enableAutomaticPings(15, TimeUnit.SECONDS);
            if (ctx.session.getRemoteAddress() instanceof InetSocketAddress inetSocketAddress) {
                if (!webContainer.allowAttemptLogin(inetSocketAddress.getHostString())) {
                    ctx.closeSession(WsCloseStatus.TRY_AGAIN_LATER, "IP banned due too many failed authenticating tries");
                    return;
                }
            }
            connectedWebSocketSessions.put(ctx, new WebSocketSession());
            ctx.send(new WebSocketServerMessage<>(UUID.randomUUID().toString(), "motd", "PeerBanHelper Remote Console - Connection established\nPlease send login payload in 15 seconds before remote shutdown this session. You may won't receive any message before logged in."));
        });
        wsConfig.onMessage(ctx -> {
            if (ctx.message().equalsIgnoreCase("PING")) {
                ctx.send("PONG");
                return;
            }
            WebSocketSession session = connectedWebSocketSessions.get(ctx);
            if (session == null) {
                ctx.closeSession(WsCloseStatus.SERVER_ERROR, "Session not found, unable to handle WebSocket connection, please reconnect.");
                return;
            }
            WebSocketClientMessage clientMessage = ctx.messageAsClass(WebSocketClientMessageBase.class);
            handleClientMessage(ctx, session, clientMessage);
        });
        wsConfig.onClose(this::revokeWebSocketSession);
        wsConfig.onError(this::revokeWebSocketSession);
    }

    private void handleClientMessage(WsMessageContext ctx, WebSocketSession session, WebSocketClientMessage clientMessage) {
        switch (clientMessage.getType()) {
            case "login" -> handleClientLoginMessage(ctx, session, clientMessage);
            case "history" -> handleClientHistoryMessage(ctx, session, clientMessage);
            default ->
                    ctx.send(new WebSocketServerMessage<>(clientMessage.getMsgId(), "undefined", "Unknown type, please check API documentation."));
        }
    }

    private void handleClientHistoryMessage(WsMessageContext ctx, WebSocketSession session, WebSocketClientMessage clientMessage) {
        if (!session.isLoggedIn()) {
            ctx.closeSession(WsCloseStatus.POLICY_VIOLATION, "Access denied, You are not allowed access any endpoint except login before logged in.");
            return;
        }
        WebSocketClientRequestHistoryMessage historyMessage = ctx.messageAsClass(WebSocketClientRequestHistoryMessage.class);
        ctx.send(new WebSocketServerMessage<>(historyMessage.getMsgId(), "history", MemoryLoggerAppender.getLogs()));
    }

    private void handleClientLoginMessage(WsMessageContext ctx, WebSocketSession session, WebSocketClientMessage clientMessage) {
        WebSocketClientLoginMessage loginMessage = ctx.messageAsClass(WebSocketClientLoginMessage.class);
        String token = loginMessage.getToken();
        String ip = null;
        if(ctx.session.getRemoteAddress() instanceof InetSocketAddress inetSocketAddress){
            ip = inetSocketAddress.getHostString();
        }
        if(ip != null){
            if(!webContainer.allowAttemptLogin(ip)){
                ctx.closeSession(WsCloseStatus.TRY_AGAIN_LATER, "IP banned due too many failed authenticating tries, login message is not accepted anymore.");
                return;
            }
        }
        if (webContainer.getToken().equals(token)) {
            session.setLoggedIn(true);
            ctx.send(new WebSocketServerMessage<>(loginMessage.getMsgId(), "loginResult", true));
            if(ip != null){
                webContainer.markLoginSuccess(ip);
            }
        } else {
            ctx.send(new WebSocketServerMessage<>(loginMessage.getMsgId(), "loginResult", false));
            if(ip != null){
                webContainer.markLoginFailed(ip);
                if(!webContainer.allowAttemptLogin(ip)){
                    ctx.closeSession(WsCloseStatus.TRY_AGAIN_LATER, "Closing connection, IP banned due too many failed authenticating tries");
                }
            }
        }
    }

    private void revokeWebSocketSession(WsContext wsContext) {
        UUID sessionId = getWebSocketSessionId(wsContext);
        if (sessionId != null) {
            connectedWebSocketSessions.remove(wsContext);
        }
    }

    private UUID getWebSocketSessionId(WsContext wsContext) {
        var attr = wsContext.attribute("sessionId");
        if (attr instanceof UUID sessionId) {
            return sessionId;
        }
        return null;
    }

    private void handleLogs(Context ctx) {
        ctx.status(200);
        ctx.json(new StdResp(true, null, Map.of("logs", MemoryLoggerAppender.getLogs())));
    }


    @Override
    public void onDisable() {

        Main.getEventBus().unregister(this);
        scheduledService.shutdown();
    }


    public record WebSocketServerMessage<T>(String msgId, String type, T data) {
    }

    @EqualsAndHashCode(callSuper = true)
    @Getter
    @Setter
    public static class WebSocketClientLoginMessage extends WebSocketClientMessageBase {
        private String token;

        public WebSocketClientLoginMessage(String msgId, String type, String token) {
            super(msgId, type);
            this.token = token;
        }
    }

    @EqualsAndHashCode(callSuper = true)
    @Getter
    @Setter
    public static class WebSocketClientRequestHistoryMessage extends WebSocketClientMessageBase {
        public WebSocketClientRequestHistoryMessage(String msgId, String type) {
            super(msgId, type);
        }
    }

    public interface WebSocketClientMessage {
        String getMsgId();

        String getType();
    }

    @Getter
    @Setter
    public static class WebSocketClientMessageBase implements WebSocketClientMessage {
        private String msgId;
        private String type;

        public WebSocketClientMessageBase(String msgId, String type) {
            this.msgId = msgId;
            this.type = type;
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    private static class WebSocketSession {
        private boolean loggedIn = false;
        private long connectedAt = System.currentTimeMillis();
    }
}
