package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.BanList;
import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.database.dao.impl.ModuleDao;
import com.ghostchu.peerbanhelper.database.dao.impl.RuleDao;
import com.ghostchu.peerbanhelper.database.dao.impl.TorrentDao;
import com.ghostchu.peerbanhelper.downloader.DownloaderManagerImpl;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.BanDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.BanLogDTO;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.ipdb.IPDBManager;
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
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Component

public final class PBHBanController extends AbstractFeatureModule {
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
    @Autowired
    private BanList banList;
    @Autowired
    private IPDBManager iPDBManager;

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
                .delete("/api/bans", this::handleBanDelete, Role.USER_WRITE)
                .put("/api/bans", this::handleBanAdd, Role.USER_WRITE);
    }

    private void handleBanAdd(@NotNull Context context) {
        List<String> request = Arrays.asList(context.bodyAsClass(String[].class));
        int size = 0;
        for (String s : request) {
            downloaderServer.scheduleBanPeerNoAssign(new PeerAddress(s, 0, s));
            size++;
        }
        context.json(new StdResp(true, null, Map.of("count", size)));
    }

    private void handleBanDelete(Context context) {
        List<String> request = Arrays.asList(context.bodyAsClass(String[].class));
        List<IPAddress> pendingRemovals = new ArrayList<>();
        if (request.contains("*")) {
            pendingRemovals.addAll(banList.copyKeySet());
            downloaderServer.getNeedReApplyBanList().set(true);
        } else {
            for (String s : request) {
                pendingRemovals.add(IPAddressUtil.getIPAddress(s));
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
        var queryResult = historyDao.queryByPaging(orderable
                .addMapping("torrent.name", "torrentName")
                .addMapping("torrent.infoHash", "torrentInfoHash")
                .addMapping("torrent.size", "torrentSize")
                .addMapping("module.name", "module")
                .addMapping("rule.rule", "rule")
                .addMapping("ip", "peerIp")  // IP地址排序映射
                .apply(historyDao.queryBuilder()
                        .join(torrentDao.queryBuilder().setAlias("torrent"), QueryBuilder.JoinType.LEFT, QueryBuilder.JoinWhereOperation.AND)
                        .join(ruleDao.queryBuilder().setAlias("rule")
                                        .join(moduleDao.queryBuilder().setAlias("module"), QueryBuilder.JoinType.LEFT, QueryBuilder.JoinWhereOperation.AND)
                                , QueryBuilder.JoinType.LEFT, QueryBuilder.JoinWhereOperation.AND)
                ), pageable);
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
        if (search != null) search = URLDecoder.decode(search, StandardCharsets.UTF_8);
        /* ---------------- Pagination path ---------------- */
        Pageable pageable = new Pageable(ctx); // reads page & pageSize

        // We always sort by ban time (desc) as default
        var banStream = getBanResponseStream(locale(ctx),
                -1,       // lastBanTime unused
                -1,       // limit unused
                ignoreBanForDisconnect,
                search);

        List<BanDTO> allResults = banStream.toList();
        long total = allResults.size();

        long skip = pageable.getZeroBasedPage() * pageable.getSize();
        List<BanDTO> pageResults = allResults.stream()
                .skip(skip)
                .limit(pageable.getSize())
                .toList();

        ctx.json(new StdResp(true, null, new Page<>(pageable, total, pageResults)));
    }

    @Override
    public void onDisable() {

    }

    private @NotNull Stream<BanDTO> getBanResponseStream(String locale, long lastBanTime, long limit, boolean ignoreBanForDisconnect, String search) {
        var banResponseList = banList.toMap()
                .entrySet()
                .stream()
                .filter(b -> {
                    if(b.getValue().isExcludeFromDisplay()) return false;
                    if (!ignoreBanForDisconnect) return true;
                    return !b.getValue().isBanForDisconnect();
                })
                .filter(b -> search == null
                        || Arrays.stream(b.getKey().toStandardStrings()).anyMatch(ip -> ip.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
                        || b.getValue().toString().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
                .map(entry -> new BanDTO(entry.getKey().toNormalizedString(), new BakedBanMetadata(locale, entry.getValue()), null))
                .sorted((o1, o2) -> Long.compare(o2.getBanMetadata().getBanAt(), o1.getBanMetadata().getBanAt()));
        if (lastBanTime > 0) {
            banResponseList = banResponseList.filter(b -> b.getBanMetadata().getBanAt() < lastBanTime);
        }
        if (limit > 0) {
            banResponseList = banResponseList.limit(limit);
        }
        banResponseList = banResponseList.peek(response -> {
            PeerWrapper peerWrapper = response.getBanMetadata().getPeer();
            if (peerWrapper != null) {
                var nullableGeoData = iPDBManager.queryIPDB(peerWrapper.toPeerAddress().getAddress().toInetAddress()).geoData().get();
                response.setIpGeoData(nullableGeoData);
            }
        });
        return banResponseList;
    }
}
