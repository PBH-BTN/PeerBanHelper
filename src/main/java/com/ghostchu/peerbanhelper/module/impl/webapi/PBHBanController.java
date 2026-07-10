package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.baomidou.mybatisplus.core.metadata.IPage;
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
import io.javalin.openapi.*;
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
        webContainer.routes()
                .get("/api/bans", this::handleBans, Role.USER_READ)
                .get("/api/bans/logs", this::handleLogs, Role.USER_READ)
                .get("/api/bans/ranks", this::handleRanks, Role.USER_READ)
                .delete("/api/bans", this::handleBanDelete, Role.USER_WRITE)
                .put("/api/bans", this::handleBanAdd, Role.USER_WRITE);
    }

    @OpenApi(
            path = "/api/bans",
            methods = HttpMethod.PUT,
            summary = "添加封禁条目",
            description = "添加一个或多个封禁条目到当前封禁列表",
            tags = {"封禁管理"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = String[].class)),
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleBanAdd"
    )
    private void handleBanAdd(@NotNull Context context) {
        String[] request = context.bodyAsClass(String[].class);
        int size = 0;
        for (String s : request) {
            downloaderServer.scheduleBanPeerNoAssign(new PeerAddress(s, 0, s));
            size++;
        }
        context.json(new StdResp(true, null, Map.of("count", size)));
    }

    @OpenApi(
            path = "/api/bans",
            methods = HttpMethod.DELETE,
            summary = "删除封禁条目",
            description = "删除一个或多个封禁条目，支持使用 * 清空全部封禁",
            tags = {"封禁管理"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = String[].class)),
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleBanDelete"
    )
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

    @OpenApi(
            path = "/api/bans/ranks",
            methods = HttpMethod.GET,
            summary = "获取封禁排行",
            description = "分页获取封禁 IP 的排行数据",
            tags = {"封禁管理"},
            queryParams = {
                    @OpenApiParam(name = "filter", description = "用于筛选排行结果的关键字")
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleRanks"
    )
    private void handleRanks(Context ctx) {
        Pageable pageable = new Pageable(ctx);
        String filter = ctx.queryParam("filter");
        ctx.json(new StdResp(true, null, PBHPage.from(historyService.getBannedIps(pageable.toPage(), filter))));
    }

    @OpenApi(
            path = "/api/bans/logs",
            methods = HttpMethod.GET,
            summary = "获取封禁日志",
            description = "分页获取封禁日志记录列表",
            tags = {"封禁管理"},
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "banLogs"
    )
    private void handleLogs(Context ctx) {
        persistMetrics.flush();
        Pageable pageable = new Pageable(ctx);
        Page<HistoryEntity> pageRequest = pageable.toPage();

        IPage<HistoryEntity> pageResult = historyService.getBanLogs(pageRequest,
                new Orderable(Map.of("ban_at", false), ctx)
                        .addRemapping("banAt", "ban_at")
                        .addRemapping("unbanAt", "unban_at")
                        .addRemapping("peerIp", "ip")
                        .addRemapping("peerPort", "port")
                        .addRemapping("peerId", "peer_id")
                        .addRemapping("peerClientName", "peer_client_name")
                        .addRemapping("peerUploaded", "peer_uploaded")
                        .addRemapping("peerDownloaded", "peer_downloaded")
                        .addRemapping("peerProgress", "peer_progress")
                        .addRemapping("torrentInfoHash", "torrent_info_hash")
                        .addRemapping("module", "module_name")
                        .addRemapping("rule", "rule_name")
                        .addRemapping("description", "description")
        );

        List<BanLogDTO> result = pageResult.getRecords().stream().map(r -> {
            var torrent = torrentService.getById(r.getTorrentId());
            return new BanLogDTO(ctx.req().getLocale().toString(), downloaderManager, r, TorrentEntityDTO.from(torrent));
        }).toList();

        ctx.json(new StdResp(true, null, new PBHPage<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal(), result)));
    }

    @OpenApi(
            path = "/api/bans",
            methods = HttpMethod.GET,
            summary = "获取当前封禁列表",
            description = "分页获取当前封禁列表，并支持按关键字和断连封禁状态筛选",
            tags = {"封禁管理"},
            queryParams = {
                    @OpenApiParam(name = "ignoreBanForDisconnect", description = "是否忽略因断连产生的封禁"),
                    @OpenApiParam(name = "search", description = "用于搜索 IP 或封禁信息的关键字")
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleBans"
    )
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
                .map(entry -> new BanDTO(entry.getKey().toCompressedString(), new BakedBanMetadata(locale, entry.getValue()), null))
                .sorted((o1, o2) -> o2.getBanMetadata().getBanAt().compareTo(o1.getBanMetadata().getBanAt()));

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
