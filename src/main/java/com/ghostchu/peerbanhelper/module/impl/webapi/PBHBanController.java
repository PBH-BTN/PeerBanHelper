package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.HistoryDao;
import com.ghostchu.peerbanhelper.database.table.HistoryEntity;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Component
public class PBHBanController extends AbstractFeatureModule {
    @Autowired
    private Database db;
    @Autowired
    @Qualifier("persistMetrics")
    private BasicMetrics persistMetrics;
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private HistoryDao historyDao;

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
        webContainer.javalin()
                .get("/api/bans", this::handleBans, Role.USER_READ)
                .get("/api/bans/logs", this::handleLogs, Role.USER_READ)
                .get("/api/bans/ranks", this::handleRanks, Role.USER_READ)
                .delete("/api/bans", this::handleBanDelete, Role.USER_WRITE);
    }

    private void handleBanDelete(Context context) {
        UnbanRequest request = context.bodyAsClass(UnbanRequest.class);
        List<PeerAddress> pendingRemovals = new ArrayList<>();
        for (PeerAddress address : getServer().getBannedPeers().keySet()) {
            if (request.ips().contains(address.getIp())) {
                pendingRemovals.add(address);
            }
        }
        pendingRemovals.forEach(pa -> getServer().scheduleUnBanPeer(pa));
        context.status(HttpStatus.OK);
        context.json(Map.of("count", pendingRemovals.size()));
    }


    private void handleRanks(Context ctx) {
        int number = Integer.parseInt(Objects.requireNonNullElse(ctx.queryParam("limit"), "50"));
        try {
            Map<String, Long> countMap = historyDao.getBannedIps(number);
            List<HistoryEntry> list = new ArrayList<>(countMap.size());
            countMap.forEach((k, v) -> {
                if (v >= 2) {
                    list.add(new HistoryEntry(k, v));
                }
            });
            ctx.status(HttpStatus.OK);
            ctx.json(list);
        } catch (Exception e) {
            log.error("Error on handling Web API request", e);
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
        persistMetrics.flush();
        int pageIndex = Integer.parseInt(Objects.requireNonNullElse(ctx.queryParam("pageIndex"), "0"));
        int pageSize = Integer.parseInt(Objects.requireNonNullElse(ctx.queryParam("pageSize"), "100"));
        try {

            Map<String, Object> map = new HashMap<>();
            map.put("pageIndex", pageIndex);
            map.put("pageSize", pageSize);
            map.put("results", historyDao.queryBuilder().offset((long) pageIndex).limit((long) pageSize).query()
                    .stream().map(BanLogResponse::new).toList());
            map.put("total", historyDao.countOf());
            ctx.status(HttpStatus.OK);
            ctx.json(map);
        } catch (Exception e) {
            log.error(Lang.WEB_BANLOGS_INTERNAL_ERROR, e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("message", "Internal server error"));
        }
    }

    private void handleBans(Context ctx) {
        long limit = Long.parseLong(Objects.requireNonNullElse(ctx.queryParam("limit"), "-1"));
        long lastBanTime = Long.parseLong(Objects.requireNonNullElse(ctx.queryParam("lastBanTime"), "-1"));
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

    public record UnbanRequest(List<String> ips) {
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    static class BanLogResponse {
        private long banAt;
        private long unbanAt;
        private String peerIp;
        private int peerPort;
        private String peerId;
        private String peerClientName;
        private long peerUploaded;
        private long peerDownloaded;
        private double peerProgress;
        private String torrentInfoHash;
        private String torrentName;
        private long torrentSize;
        private String module;
        private String rule;
        private String description;

        public BanLogResponse(HistoryEntity history) {
            this.banAt = history.getBanAt().getTime();
            this.unbanAt = history.getUnbanAt().getTime();
            this.peerIp = history.getIp();
            this.peerPort = history.getPort();
            this.peerId = history.getPeerIdentity().getPeerId();
            this.peerClientName = history.getPeerIdentity().getClientName();
            this.peerUploaded = history.getPeerUploaded();
            this.peerDownloaded = history.getPeerDownloaded();
            this.peerProgress = history.getPeerProgress();
            this.torrentInfoHash = history.getTorrent().getInfoHash();
            this.torrentName = history.getTorrent().getName();
            this.torrentSize = history.getTorrent().getSize();
            this.module = history.getRule().getModule().getName();
            this.rule = history.getRule().getRule();
            this.description = history.getRule().getRule();
        }
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
