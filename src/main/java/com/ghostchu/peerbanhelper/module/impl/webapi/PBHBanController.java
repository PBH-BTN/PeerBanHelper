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

    private void handleBanDelete(Context context) {
        List<String> request = Arrays.asList(context.bodyAsClass(String[].class));
        List<PeerAddress> pendingRemovals = new ArrayList<>();
        for (PeerAddress address : getServer().getBannedPeers().keySet()) {
            if (request.contains(address.getIp())) {
                pendingRemovals.add(address);
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

    private void handleBans(Context ctx) {
        long limit = Long.parseLong(Objects.requireNonNullElse(ctx.queryParam("limit"), "-1"));
        long lastBanTime = Long.parseLong(Objects.requireNonNullElse(ctx.queryParam("lastBanTime"), "-1"));
        boolean ignoreBanForDisconnect = Boolean.parseBoolean(Objects.requireNonNullElse(ctx.queryParam("ignoreBanForDisconnect"), "true"));
        var banResponseList = getBanResponseStream(locale(ctx), lastBanTime, limit, ignoreBanForDisconnect);
        ctx.json(new StdResp(true, null, banResponseList.toList()));
    }

    @Override
    public void onDisable() {

    }


    private @NotNull Stream<BanResponse> getBanResponseStream(String locale, long lastBanTime, long limit, boolean ignoreBanForDisconnect) {
        var banResponseList = getServer().getBannedPeers()
                .entrySet()
                .stream()
                .filter(b -> {
                    if(!ignoreBanForDisconnect) return true;
                    return !b.getValue().isBanForDisconnect();
                })
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
