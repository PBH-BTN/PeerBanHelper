package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.log4j2.MemoryLoggerAppender;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.StringJoiner;

@Component
public class PBHLogsController extends AbstractFeatureModule {
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
        webContainer.javalin().get("/api/logs/main", this::handleLogs, Role.USER_READ);
    }

    private void handleLogs(Context ctx) {
        ctx.status(200);
        StringJoiner joiner = new StringJoiner("\n");
        MemoryLoggerAppender.getLogs().forEach(joiner::add);
        ctx.json(Map.of("logs", joiner.toString()));
    }


    @Override
    public void onDisable() {

    }
}
