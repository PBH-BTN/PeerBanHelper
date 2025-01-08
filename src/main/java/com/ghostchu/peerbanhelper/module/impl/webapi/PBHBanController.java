package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.database.table.HistoryEntity;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.util.paging.Page;
import com.ghostchu.peerbanhelper.util.paging.Pageable;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.peerbanhelper.wrapper.BakedBanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.wrapper.PeerWrapper;
import io.javalin.http.Context;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Stream;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Slf4j
@Component
@IgnoreScan
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

    /**
     * Handles the deletion of banned peers based on the provided request.
     *
     * This method processes a request to remove banned peers from the system. It supports two modes of deletion:
     * 1. Wildcard deletion: If the request contains "*", all currently banned peers are removed.
     * 2. Selective deletion: Removes specific peers whose IP addresses match those in the request.
     *
     * @param context The Javalin request context containing the list of IPs to unban
     * @throws IllegalArgumentException if the request body cannot be parsed as a string array
     */
    private void handleBanDelete(Context context) {
        List<String> request = Arrays.asList(context.bodyAsClass(String[].class));
        List<PeerAddress> pendingRemovals = new ArrayList<>();
        if (request.contains("*")) {
            pendingRemovals.addAll(getServer().getBannedPeers().keySet());
            getServer().getNeedReApplyBanList().set(true);
        } else {
            for (PeerAddress address : getServer().getBannedPeers().keySet()) {
                if (request.contains(address.getIp())) {
                    pendingRemovals.add(address);
                }
            }
        }
        pendingRemovals.forEach(pa -> getServer().scheduleUnBanPeer(pa));
        context.json(new StdResp(true, null, Map.of("count", pendingRemovals.size())));
    }

    private void handleRanks(Context ctx) throws Exception {
        Pageable pageable = new Pageable(ctx);
        String filter = ctx.queryParam("filter");
        ctx.json(new StdResp(true, null, historyDao.getBannedIps(pageable, filter)));
    }

    private void handleLogs(Context ctx) throws SQLException {
        if (db == null) {
            throw new IllegalStateException("Database not initialized on this PeerBanHelper server");
        }
        persistMetrics.flush();
        Pageable pageable = new Pageable(ctx);
        var queryResult = historyDao.queryByPaging(historyDao.queryBuilder().orderBy("banAt", false), pageable);
        var result = queryResult.getResults().stream().map(r -> new BanLogResponse(locale(ctx), r)).toList();
        ctx.json(new StdResp(true, null, new Page<>(pageable, queryResult.getTotal(), result)));
    }

    /**
     * Handles a request to retrieve current bans with optional filtering and pagination.
     *
     * @param ctx The Javalin context containing the HTTP request parameters
     *            Supports query parameters:
     *            - limit: Maximum number of ban entries to return (default: -1, returns all)
     *            - lastBanTime: Timestamp to retrieve bans after this time (default: -1, no time filter)
     *            - ignoreBanForDisconnect: Flag to exclude bans for disconnected peers (default: true)
     *            - search: Optional search query to filter ban entries by IP or metadata
     *
     * @throws NumberFormatException if limit or lastBanTime cannot be parsed as long
     * @throws IllegalArgumentException if query parameters are invalid
     */
    private void handleBans(Context ctx) {
        long limit = Long.parseLong(Objects.requireNonNullElse(ctx.queryParam("limit"), "-1"));
        long lastBanTime = Long.parseLong(Objects.requireNonNullElse(ctx.queryParam("lastBanTime"), "-1"));
        boolean ignoreBanForDisconnect = Boolean.parseBoolean(Objects.requireNonNullElse(ctx.queryParam("ignoreBanForDisconnect"), "true"));
        var search = ctx.queryParam("search");
        var banResponseList = getBanResponseStream(locale(ctx), lastBanTime, limit, ignoreBanForDisconnect, search);
        ctx.json(new StdResp(true, null, banResponseList.toList()));
    }

    /**
     * Handles the disabling of the PBH Ban Controller module.
     * 
     * This method is called when the module is being disabled or shut down. 
     * Currently, it does not perform any specific actions during module disablement.
     * 
     * @implNote This is an empty implementation, which means no cleanup or 
     *           special shutdown procedures are currently defined for this module.
     */
    @Override
    public void onDisable() {

    }


    /**
     * Generates a stream of ban responses with optional filtering and enrichment.
     *
     * @param locale The locale for localization of ban metadata
     * @param lastBanTime Timestamp to filter bans older than this value (0 for no time filter)
     * @param limit Maximum number of ban responses to return (0 for no limit)
     * @param ignoreBanForDisconnect Flag to exclude bans triggered by disconnections
     * @param search Optional search query to filter ban entries by address or metadata
     * @return A stream of ban responses, sorted by ban timestamp in descending order
     */
    private @NotNull Stream<BanResponse> getBanResponseStream(String locale, long lastBanTime, long limit, boolean ignoreBanForDisconnect, String search) {
        var banResponseList = getServer().getBannedPeers()
                .entrySet()
                .stream()
                .filter(b -> {
                    if (!ignoreBanForDisconnect) return true;
                    return !b.getValue().isBanForDisconnect();
                })
                .filter(b -> search == null || b.getKey().toString().contains(search) || b.getValue().toString().contains(search))
                .map(entry -> new BanResponse(entry.getKey().getAddress().toString(), new BakedBanMetadata(locale, entry.getValue())))
                .sorted((o1, o2) -> Long.compare(o2.getBanMetadata().getBanAt(), o1.getBanMetadata().getBanAt()));
        if (lastBanTime > 0) {
            banResponseList = banResponseList.filter(b -> b.getBanMetadata().getBanAt() < lastBanTime);
        }
        if (limit > 0) {
            banResponseList = banResponseList.limit(limit);
        }
        banResponseList = banResponseList.peek(meta -> {
            PeerWrapper peerWrapper = meta.getBanMetadata().getPeer();
            if (peerWrapper != null) {
                var nullableGeoData = getServer().queryIPDB(peerWrapper.toPeerAddress()).geoData().get();
                meta.getBanMetadata().setGeo(nullableGeoData);
            }
        });
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
        private String downloader;

        public BanLogResponse(String locale, HistoryEntity history) {
            this.banAt = history.getBanAt().getTime();
            this.unbanAt = history.getUnbanAt().getTime();
            this.peerIp = history.getIp();
            this.peerPort = history.getPort();
            this.peerId = history.getPeerId();
            this.peerClientName = history.getPeerClientName();
            this.peerUploaded = history.getPeerUploaded();
            this.peerDownloaded = history.getPeerDownloaded();
            this.peerProgress = history.getPeerProgress();
            this.torrentInfoHash = history.getTorrent().getInfoHash();
            this.torrentName = history.getTorrent().getName();
            this.torrentSize = history.getTorrent().getSize();
            this.module = history.getRule().getModule().getName();
            this.rule = tl(locale, history.getRule().getRule());
            this.description = tl(locale, history.getDescription());
            this.downloader = history.getDownloader();
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    static class BanResponse {
        private String address;
        private BakedBanMetadata banMetadata;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    static class HistoryEntry {
        private String address;
        private long count;
    }

}
