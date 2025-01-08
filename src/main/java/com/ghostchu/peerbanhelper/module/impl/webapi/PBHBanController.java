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
     * 1. Wildcard deletion: If the request contains "*", all currently banned peers are scheduled for removal.
     * 2. Selective deletion: If specific IP addresses are provided, only those matching banned peers are removed.
     *
     * @param context The Javalin request context containing the deletion request
     * @throws IllegalArgumentException if the request body cannot be parsed
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
     * Handles the retrieval of banned peers with optional filtering and pagination.
     *
     * @param ctx the Javalin context containing query parameters for filtering and pagination
     *
     * @queryParam limit maximum number of ban entries to return (default: -1, meaning no limit)
     * @queryParam lastBanTime timestamp to start retrieving ban entries from (default: -1)
     * @queryParam ignoreBanForDisconnect flag to filter out bans related to disconnections (default: true)
     * @queryParam search optional search term to filter ban entries by peer address or metadata
     *
     * @return JSON response containing a list of banned peers matching the specified criteria
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
     * Handles the disabling of the PBHBanController module.
     * 
     * This method is called when the module is being shut down or disabled. 
     * Currently, it does not perform any specific actions during module disablement.
     * 
     * @implNote This is an empty implementation that can be extended in the future 
     *           to perform cleanup or release resources if needed.
     */
    @Override
    public void onDisable() {

    }


    /**
     * Retrieves a stream of banned peers with optional filtering and metadata enrichment.
     *
     * @param locale The locale for localizing ban metadata
     * @param lastBanTime Optional timestamp to filter bans before this time
     * @param limit Maximum number of ban entries to return
     * @param ignoreBanForDisconnect Flag to exclude bans triggered by disconnection
     * @param search Optional search term to filter ban entries by address or metadata
     * @return A stream of {@link BanResponse} objects representing banned peers
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
