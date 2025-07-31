package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.database.dao.impl.ModuleDao;
import com.ghostchu.peerbanhelper.database.dao.impl.RuleDao;
import com.ghostchu.peerbanhelper.database.dao.impl.TorrentDao;
import com.ghostchu.peerbanhelper.database.table.HistoryEntity;
import com.ghostchu.peerbanhelper.downloader.DownloaderManagerImpl;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.BanDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.BanLogDTO;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import com.ghostchu.peerbanhelper.util.query.Page;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.peerbanhelper.wrapper.BakedBanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.wrapper.PeerWrapper;
import com.j256.ormlite.stmt.QueryBuilder;
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

@Slf4j
@Component

public final class PBHBanController extends AbstractFeatureModule {
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BanListFilters {
        private String reason;
        private String clientName;
        private String peerId;
        private String country;
        private String city;
        private String asn;
        private String isp;
        private String netType;
        private String context;
        private String rule;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BanLogFilters {
        private String reason;
        private String clientName;
        private String peerId;
        private String country;
        private String city;
        private String asn;
        private String isp;
        private String netType;
        private String context;
        private String rule;
        private String torrentName;
        private String module;
    }

    @Autowired
    private Database db;
    @Autowired
    @Qualifier("persistMetrics")
    private BasicMetrics persistMetrics;
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private HistoryDao historyDao;
    @Autowired
    private DownloaderServer downloaderServer;
    @Autowired
    private DownloaderManagerImpl downloaderManager;
    @Autowired
    private TorrentDao torrentDao;
    @Autowired
    private ModuleDao moduleDao;
    @Autowired
    private RuleDao ruleDao;

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
        List<String> request = Arrays.asList(context.bodyAsClass(String[].class));
        List<PeerAddress> pendingRemovals = new ArrayList<>();
        if (request.contains("*")) {
            pendingRemovals.addAll(downloaderServer.getBannedPeers().keySet());
            downloaderServer.getNeedReApplyBanList().set(true);
        } else {
            for (PeerAddress address : downloaderServer.getBannedPeers().keySet()) {
                if (request.contains(address.getIp())) {
                    pendingRemovals.add(address);
                }
            }
        }
        pendingRemovals.forEach(pa -> downloaderServer.scheduleUnBanPeer(pa));
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
        Orderable orderable = new Orderable(Map.of("banAt", false), ctx);
        
        // Extract filter parameters for ban logs
        BanLogFilters filters = new BanLogFilters(
                ctx.queryParam("filterReason"),
                ctx.queryParam("filterClientName"), 
                ctx.queryParam("filterPeerId"),
                ctx.queryParam("filterCountry"),
                ctx.queryParam("filterCity"),
                ctx.queryParam("filterAsn"),
                ctx.queryParam("filterIsp"),
                ctx.queryParam("filterNetType"),
                ctx.queryParam("filterContext"),
                ctx.queryParam("filterRule"),
                ctx.queryParam("filterTorrentName"),
                ctx.queryParam("filterModule")
        );

        var queryBuilder = orderable
                .addMapping("torrent.name", "torrentName")
                .addMapping("torrent.infoHash", "torrentInfoHash")
                .addMapping("torrent.size", "torrentSize")
                .addMapping("module.name", "module")
                .addMapping("rule.rule", "rule")
                .apply(historyDao.queryBuilder()
                        .join(torrentDao.queryBuilder().setAlias("torrent"), QueryBuilder.JoinType.LEFT, QueryBuilder.JoinWhereOperation.AND)
                        .join(ruleDao.queryBuilder().setAlias("rule")
                                        .join(moduleDao.queryBuilder().setAlias("module"), QueryBuilder.JoinType.LEFT, QueryBuilder.JoinWhereOperation.AND)
                                , QueryBuilder.JoinType.LEFT, QueryBuilder.JoinWhereOperation.AND)
                );

        // Apply filters to the query
        queryBuilder = applyBanLogFilters(queryBuilder, filters);

        var queryResult = historyDao.queryByPaging(queryBuilder, pageable);
        var result = queryResult.getResults().stream().map(r -> new BanLogDTO(locale(ctx), downloaderManager, r)).toList();
        ctx.json(new StdResp(true, null, new Page<>(pageable, queryResult.getTotal(), result)));
    }

    private void handleBans(Context ctx) {
        /*
         * Two modes are supported here for backward-compatibility:
         *  1.  Old feed-stream style:   limit + lastBanTime (no page/pageSize)
         *  2.  New pagination style:    page + pageSize
         *
         *  The controller detects the presence of the `page` query parameter.
         */

        boolean hasPageParam = ctx.queryParam("page") != null;

        boolean ignoreBanForDisconnect =
                Boolean.parseBoolean(Objects.requireNonNullElse(ctx.queryParam("ignoreBanForDisconnect"), "true"));
        String search = ctx.queryParam("search");
        
        // Extract filter parameters
        BanListFilters filters = new BanListFilters(
                ctx.queryParam("filterReason"),
                ctx.queryParam("filterClientName"),
                ctx.queryParam("filterPeerId"),
                ctx.queryParam("filterCountry"),
                ctx.queryParam("filterCity"),
                ctx.queryParam("filterAsn"),
                ctx.queryParam("filterIsp"),
                ctx.queryParam("filterNetType"),
                ctx.queryParam("filterContext"),
                ctx.queryParam("filterRule")
        );

        if (hasPageParam) {
            /* ---------------- Pagination path ---------------- */
            Pageable pageable = new Pageable(ctx); // reads page & pageSize

            // We always sort by ban time (desc) as default
            var banStream = getBanResponseStream(locale(ctx),
                    -1,       // lastBanTime unused
                    -1,       // limit unused
                    ignoreBanForDisconnect,
                    search,
                    filters);

            List<BanDTO> allResults = banStream.toList();
            long total = allResults.size();

            long skip = pageable.getZeroBasedPage() * pageable.getSize();
            List<BanDTO> pageResults = allResults.stream()
                    .skip(skip)
                    .limit(pageable.getSize())
                    .toList();

            ctx.json(new StdResp(true, null, new Page<>(pageable, total, pageResults)));
        } else {
            /* ---------------- Legacy feed-stream path ---------------- */
            long limit = Long.parseLong(Objects.requireNonNullElse(ctx.queryParam("limit"), "-1"));
            long lastBanTime = Long.parseLong(Objects.requireNonNullElse(ctx.queryParam("lastBanTime"), "-1"));

            var banResponseList = getBanResponseStream(locale(ctx), lastBanTime, limit, ignoreBanForDisconnect, search, filters);
            ctx.json(new StdResp(true, null, banResponseList.toList()));
        }
    }

    @Override
    public void onDisable() {

    }


    private @NotNull Stream<BanDTO> getBanResponseStream(String locale, long lastBanTime, long limit, boolean ignoreBanForDisconnect, String search, BanListFilters filters) {
        var banResponseList = downloaderServer.getBannedPeers()
                .entrySet()
                .stream()
                .filter(b -> {
                    if (!ignoreBanForDisconnect) return true;
                    return !b.getValue().isBanForDisconnect();
                })
                .filter(b -> search == null || b.getKey().toString().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))
                        || b.getValue().toString().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
                .map(entry -> {
                    BanDTO dto = new BanDTO(entry.getKey().getAddress().toString(), new BakedBanMetadata(locale, entry.getValue()), null);
                    PeerWrapper peerWrapper = dto.getBanMetadata().getPeer();
                    if (peerWrapper != null) {
                        var nullableGeoData = getServer().queryIPDB(peerWrapper.toPeerAddress()).geoData().get();
                        dto.setIpGeoData(nullableGeoData);
                    }
                    return dto;
                })
                .filter(dto -> applyFilters(dto, filters))
                .sorted((o1, o2) -> Long.compare(o2.getBanMetadata().getBanAt(), o1.getBanMetadata().getBanAt()));
        if (lastBanTime > 0) {
            banResponseList = banResponseList.filter(b -> b.getBanMetadata().getBanAt() < lastBanTime);
        }
        if (limit > 0) {
            banResponseList = banResponseList.limit(limit);
        }
        return banResponseList;
    }

    private boolean applyFilters(BanDTO dto, BanListFilters filters) {
        if (filters == null) return true;

        BakedBanMetadata metadata = dto.getBanMetadata();
        
        // Filter by reason/description
        if (filters.getReason() != null && !filters.getReason().trim().isEmpty()) {
            String description = metadata.getDescription();
            if (description == null || !description.toLowerCase(Locale.ROOT).contains(filters.getReason().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Filter by client name
        if (filters.getClientName() != null && !filters.getClientName().trim().isEmpty()) {
            PeerWrapper peer = metadata.getPeer();
            String clientName = peer != null ? peer.getClientName() : null;
            if (clientName == null || !clientName.toLowerCase(Locale.ROOT).contains(filters.getClientName().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Filter by peer ID
        if (filters.getPeerId() != null && !filters.getPeerId().trim().isEmpty()) {
            PeerWrapper peer = metadata.getPeer();
            String peerId = peer != null ? peer.getId() : null;
            if (peerId == null || !peerId.toLowerCase(Locale.ROOT).contains(filters.getPeerId().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Filter by country
        if (filters.getCountry() != null && !filters.getCountry().trim().isEmpty()) {
            var geoData = dto.getIpGeoData();
            String country = geoData != null ? geoData.getCountry() : null;
            if (country == null || !country.toLowerCase(Locale.ROOT).contains(filters.getCountry().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Filter by city
        if (filters.getCity() != null && !filters.getCity().trim().isEmpty()) {
            var geoData = dto.getIpGeoData();
            String city = geoData != null ? geoData.getCity() : null;
            if (city == null || !city.toLowerCase(Locale.ROOT).contains(filters.getCity().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Filter by ASN
        if (filters.getAsn() != null && !filters.getAsn().trim().isEmpty()) {
            var geoData = dto.getIpGeoData();
            String asn = geoData != null ? geoData.getAsn() : null;
            if (asn == null || !asn.toLowerCase(Locale.ROOT).contains(filters.getAsn().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Filter by ISP
        if (filters.getIsp() != null && !filters.getIsp().trim().isEmpty()) {
            var geoData = dto.getIpGeoData();
            String isp = geoData != null ? geoData.getIsp() : null;
            if (isp == null || !isp.toLowerCase(Locale.ROOT).contains(filters.getIsp().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Filter by network type
        if (filters.getNetType() != null && !filters.getNetType().trim().isEmpty()) {
            var geoData = dto.getIpGeoData();
            String netType = geoData != null ? geoData.getNet() : null;
            if (netType == null || !netType.toLowerCase(Locale.ROOT).contains(filters.getNetType().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Filter by context
        if (filters.getContext() != null && !filters.getContext().trim().isEmpty()) {
            String context = metadata.getContext();
            if (context == null || !context.toLowerCase(Locale.ROOT).contains(filters.getContext().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        // Filter by rule
        if (filters.getRule() != null && !filters.getRule().trim().isEmpty()) {
            String rule = metadata.getRule();
            if (rule == null || !rule.toLowerCase(Locale.ROOT).contains(filters.getRule().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        return true;
    }

    private QueryBuilder<HistoryEntity, Long> applyBanLogFilters(QueryBuilder<HistoryEntity, Long> queryBuilder, BanLogFilters filters) throws SQLException {
        if (filters == null) return queryBuilder;

        // Filter by reason/description
        if (filters.getReason() != null && !filters.getReason().trim().isEmpty()) {
            queryBuilder.where().like("description", "%" + filters.getReason() + "%");
        }

        // Filter by client name
        if (filters.getClientName() != null && !filters.getClientName().trim().isEmpty()) {
            queryBuilder.where().like("peerClientName", "%" + filters.getClientName() + "%");
        }

        // Filter by peer ID
        if (filters.getPeerId() != null && !filters.getPeerId().trim().isEmpty()) {
            queryBuilder.where().like("peerId", "%" + filters.getPeerId() + "%");
        }

        // Note: Geographic filters (country, city, ASN, ISP, netType) would require additional
        // GeoIP lookup during query or stored geo data in the history table.
        // For now, these filters are not implemented in the database query.

        // Filter by context
        if (filters.getContext() != null && !filters.getContext().trim().isEmpty()) {
            queryBuilder.where().like("context", "%" + filters.getContext() + "%");
        }

        // Filter by rule
        if (filters.getRule() != null && !filters.getRule().trim().isEmpty()) {
            queryBuilder.where().like("rule.rule", "%" + filters.getRule() + "%");
        }

        // Filter by torrent name
        if (filters.getTorrentName() != null && !filters.getTorrentName().trim().isEmpty()) {
            queryBuilder.where().like("torrent.name", "%" + filters.getTorrentName() + "%");
        }

        // Filter by module
        if (filters.getModule() != null && !filters.getModule().trim().isEmpty()) {
            queryBuilder.where().like("module.name", "%" + filters.getModule() + "%");
        }

        return queryBuilder;
    }
}
