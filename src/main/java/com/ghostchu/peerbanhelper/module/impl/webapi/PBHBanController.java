package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.database.DatabaseHelper;
import com.ghostchu.peerbanhelper.metric.impl.persist.PersistMetrics;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
public class PBHBanController extends AbstractFeatureModule {
    private final DatabaseHelper db;

    public PBHBanController(PeerBanHelperServer server, YamlConfiguration profile, DatabaseHelper db) {
        super(server, profile);
        this.db = db;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - PBH Ban API";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-downloader";
    }

    @Override
    public void onEnable() {
        getServer().getWebContainer().javalin()
                .get("/api/bans", this::handleBans, Role.USER_READ)
                .get("/api/bans/logs", this::handleLogs, Role.USER_READ)
                .get("/api/bans/ranks", this::handleRanks, Role.USER_READ);
    }

    private void handleRanks(Context ctx) {
        int number = Integer.parseInt(Objects.requireNonNullElse(ctx.queryParam("limit"), "50"));
        try {
            Map<String, Long> countMap = db.findMaxBans(number);
            List<HistoryEntry> list = new ArrayList<>(countMap.size());
            countMap.forEach((k, v) -> {
                if (v >= 2) {
                    list.add(new HistoryEntry(k, v));
                }
            });
            ctx.status(HttpStatus.OK);
            ctx.json(list);
        } catch (SQLException e) {
            log.warn("Error on handling Web API request", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("message", "Internal server error"));
        }
    }

    private void handleLogs(Context ctx) {
        if (db == null) {
            ctx.status(HttpStatus.NO_CONTENT);
            ctx.json(Map.of("message", "Database not initialized on this PeerBanHelper server"));
            return;
        }
        if (getServer().getMetrics() instanceof PersistMetrics persistMetrics) {
            persistMetrics.flush();
        }
        int pageIndex = Integer.parseInt(Objects.requireNonNullElse(ctx.header("pageIndex"), "0"));
        int pageSize = Integer.parseInt(Objects.requireNonNullElse(ctx.header("pageSize"), "100"));
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("pageIndex", pageIndex);
            map.put("pageSize", pageSize);
            map.put("results", db.queryBanLogs(null, null, pageIndex, pageSize));
            map.put("total", db.queryBanLogsCount());
            ctx.status(HttpStatus.OK);
            ctx.json(map);
        } catch (SQLException e) {
            log.error(Lang.WEB_BANLOGS_INTERNAL_ERROR, e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("message", "Internal server error"));
        }
    }

    private void handleBans(Context ctx) {
        long limit = Long.parseLong(Objects.requireNonNullElse(ctx.header("limit"), "-1"));
        long lastBanTime = Long.parseLong(Objects.requireNonNullElse(ctx.header("lastBanTime"), "-1"));
        var banResponseList = getBanResponseStream(lastBanTime, limit);
        ctx.status(HttpStatus.OK);
        ctx.json(banResponseList.toList());
    }

    @Override
    public void onDisable() {

    }


    private @NotNull Stream<BanResponse> getBanResponseStream(long lastBanTime, long limit) {
        var banResponseList = getServer().getBannedPeers()
                .entrySet()
                .stream()
                .map(entry -> new BanResponse(entry.getKey().getAddress().toString(), entry.getValue()))
                .sorted((o1, o2) -> Long.compare(o2.getBanMetadata().getBanAt(), o1.getBanMetadata().getBanAt()));
        if (lastBanTime > 0) {
            banResponseList = banResponseList.filter(b -> b.getBanMetadata().getBanAt() < lastBanTime);
        }
        if (limit > 0) {
            banResponseList = banResponseList.limit(limit);
        }
        return banResponseList;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    static class BanResponse {
        private String address;
        private BanMetadata banMetadata;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    static class HistoryEntry {
        private String address;
        private long count;
    }
}
