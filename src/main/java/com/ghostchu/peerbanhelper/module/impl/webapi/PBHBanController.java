package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.peerbanhelper.BanList;
import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.databasent.service.HistoryService;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.downloader.DownloaderManagerImpl;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.BanDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.BanLogDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.TorrentEntityDTO;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.ipdb.IPDBManager;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import com.ghostchu.peerbanhelper.util.query.PBHPage;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.peerbanhelper.wrapper.BakedBanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.wrapper.PeerWrapper;
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Component

public final class PBHBanController extends AbstractFeatureModule {
    @Autowired
    @Qualifier("persistMetrics")
    private BasicMetrics persistMetrics;
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private HistoryService historyService;
    @Autowired
    private DownloaderServer downloaderServer;
    @Autowired
    private DownloaderManagerImpl downloaderManager;
    @Autowired
    private TorrentService torrentService;
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
        String[] request = context.bodyAsClass(String[].class);
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

    private void handleRanks(Context ctx) {
        Pageable pageable = new Pageable(ctx);
        String filter = ctx.queryParam("filter");
        ctx.json(new StdResp(true, null, PBHPage.from(historyService.getBannedIps(pageable.toPage(), filter))));
    }

    private void handleLogs(Context ctx) {
        persistMetrics.flush();
        Pageable pageable = new Pageable(ctx);
        Page<HistoryEntity> pageRequest = pageable.toPage();

        // 构建查询条件
        QueryWrapper<HistoryEntity> queryWrapper = Wrappers.query();

        // 检查排序参数
        if (ctx.queryParam("orderBy") != null) {
            // 简单处理: 如果包含 torrentName 等，可能暂时不支持或者忽略，回退到默认
            // 或者如果 PBH 的 `Orderable` 类支持 apply 到 MP QueryWrapper。
            new Orderable(Map.of("banAt", false), ctx).apply(queryWrapper);
        } else {
            queryWrapper.orderByDesc("ban_at");
        }

        IPage<HistoryEntity> pageResult = historyService.page(pageRequest, queryWrapper);

        List<BanLogDTO> result = pageResult.getRecords().stream().map(r -> {
            var torrent = torrentService.getById(r.getTorrentId());
            return new BanLogDTO(ctx.req().getLocale().toString(), downloaderManager, r, TorrentEntityDTO.from(torrent));
        }).toList();

        ctx.json(new StdResp(true, null, new PBHPage<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal(), result)));
    }

    private void handleBans(Context ctx) {
        /*
         * Two modes are supported here for backward-compatibility:
         *  1.  Old feed-stream style:   limit + lastBanTime (no page/pageSize)
         *  2.  New pagination style:    page + pageSize
         *
         *  The controller detects the presence of the `page` query parameter.
         */

        boolean ignoreBanForDisconnect =
                Boolean.parseBoolean(Objects.requireNonNullElse(ctx.queryParam("ignoreBanForDisconnect"), "true"));
        String search = ctx.queryParam("search");
        if (search != null) search = URLDecoder.decode(search, StandardCharsets.UTF_8);
        /* ---------------- Pagination path ---------------- */
        Pageable pageable = new Pageable(ctx); // reads page & pageSize

        // We always sort by ban time (desc) as default
        var banStream = getBanResponseStream(ctx.req().getLocale().toString(),
                ignoreBanForDisconnect,
                search);

        List<BanDTO> allResults = banStream.toList();
        long total = allResults.size();

        long skip = pageable.getZeroBasedPage() * pageable.getSize();
        List<BanDTO> pageResults = allResults.stream()
                .skip(skip)
                .limit(pageable.getSize())
                .toList();

        ctx.json(new StdResp(true, null, new PBHPage<>(pageable.getPage(), pageable.getSize(), total, pageResults)));
    }

    @Override
    public void onDisable() {

    }

    private @NotNull Stream<BanDTO> getBanResponseStream(String locale, boolean ignoreBanForDisconnect, String search) {
        var banResponseList = banList.toMap()
                .entrySet()
                .stream()
                .filter(b -> {
                    if (b.getValue().isExcludeFromDisplay()) return false;
                    if (!ignoreBanForDisconnect) return true;
                    return !b.getValue().isBanForDisconnect();
                })
                .filter(b -> search == null
                        || Arrays.stream(b.getKey().toStandardStrings()).anyMatch(ip -> ip.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
                        || b.getValue().toString().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
                .map(entry -> new BanDTO(entry.getKey().toNormalizedString(), new BakedBanMetadata(locale, entry.getValue()), null))
                .sorted((o1, o2) -> Long.compare(o2.getBanMetadata().getBanAt(), o1.getBanMetadata().getBanAt()));

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
