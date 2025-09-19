package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.program.logger.NewLogEntryCreatedEvent;
import com.ghostchu.peerbanhelper.module.AbstractWebSocketFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.WebSocketLogEntryDTO;
import com.ghostchu.peerbanhelper.util.logger.JListAppender;
import com.ghostchu.peerbanhelper.util.logger.LogEntry;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.google.common.eventbus.Subscribe;
import io.javalin.http.Context;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public final class PBHLogsController extends AbstractWebSocketFeatureModule {
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
        for (WsContext wsContext : wsSessions) {
            wsContext.send(new StdResp(true, null,
                    new WebSocketLogEntryDTO(
                            event.entry().time(),
                            event.entry().thread(),
                            event.entry().level().name(),
                            event.entry().content(),
                            event.entry().seq()
                    )));
        }
    }

    private void handleLogsStream(WsConfig wsConfig) {
        acceptWebSocket(wsConfig, (ctx) -> {
            var offset = ctx.queryParam("offset");
            sendHistoryLogs(ctx, Long.parseLong(offset == null ? "0" : offset));
        });
    }

    private void sendHistoryLogs(WsContext ctx, long offset) {
        if (offset > JListAppender.getSeq().longValue()) {
            offset = 0; // PBH 重启，但是 WebUI 没有刷新
        }
        for (LogEntry logEntry : JListAppender.ringDeque) {
            if (logEntry.seq() > offset) {
                ctx.send(new StdResp(true, null,
                        new WebSocketLogEntryDTO(
                                logEntry.time(),
                                logEntry.thread(),
                                logEntry.level().name(),
                                logEntry.content(),
                                logEntry.seq()
                        )));
            }
        }
    }

    private void handleLogs(Context ctx) {
        ctx.status(200);
        var list = JListAppender.ringDeque.stream().map(e -> new WebSocketLogEntryDTO(
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

}
