package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.program.logger.NewLogEntryCreatedEvent;
import com.ghostchu.peerbanhelper.module.AbstractSSEFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.WebUILogEntryDTO;
import com.ghostchu.peerbanhelper.util.logger.JListAppender;
import com.ghostchu.peerbanhelper.util.logger.LogEntry;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.google.common.eventbus.Subscribe;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public final class PBHLogsController extends AbstractSSEFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    private static final String logEntryKey = "logEntry";

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Logs)";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-logs";
    }

    @Override
    public void onEnable() {
        webContainer.routes()
                .get("/api/logs/history", this::handleLogs, Role.USER_WRITE)
                .sse("/api/logs/live", this::handleLiveConnection, Role.USER_WRITE)
        ;
        Main.getEventBus().register(this);
    }

    private void handleLiveConnection(SseClient sseClient) {
        log.debug("Established Logs SSE Connection: {}", sseClient);
        var offset = sseClient.ctx().queryParam("offset");
        sendCurrentLogs(sseClient, Long.parseLong(offset == null ? String.valueOf(Long.MAX_VALUE) : offset));
        this.registerSseManagement(sseClient);
    }

    private void sendCurrentLogs(SseClient sseClient, long offset) {
        if (offset > JListAppender.getSeq().longValue()) { // PBH 重启，但是 WebUI 没有刷新，或者为未传递 seq 参数
            offset = 0;
            var peekedRecord = JListAppender.ringDeque.peek();
            if (peekedRecord != null) {
                var headSeq = peekedRecord.seq();
                offset = headSeq - 1;
            }
        }
        for (LogEntry logEntry : JListAppender.ringDeque) {
            if (logEntry.seq() > offset) {
                sseClient.sendEvent(new WebUILogEntryDTO(
                        logEntry.time(),
                        logEntry.thread(),
                        logEntry.level().name(),
                        logEntry.content(),
                        logEntry.seq()
                ));
            }
        }
    }

    @Subscribe
    public void onLogRecordCreated(NewLogEntryCreatedEvent event) {
        var logEntry = new WebUILogEntryDTO(
                event.entry().time(),
                event.entry().thread(),
                event.entry().level().name(),
                event.entry().content(),
                event.entry().seq()
        );
        iterateSseClients(sse -> sse.sendEvent(logEntry));
    }

    private void handleLogs(Context ctx) {
        ctx.status(200);
        var list = JListAppender.ringDeque.stream().map(e -> new WebUILogEntryDTO(
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
