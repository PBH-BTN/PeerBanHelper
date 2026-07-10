package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ability.impl.BtnAbilityIpQuery;
import com.ghostchu.peerbanhelper.databasent.dto.IPAddressTimeSeen;
import com.ghostchu.peerbanhelper.databasent.dto.IPAddressTotalTraffic;
import com.ghostchu.peerbanhelper.databasent.service.HistoryService;
import com.ghostchu.peerbanhelper.databasent.service.PeerRecordService;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.databasent.table.PeerRecordEntity;
import com.ghostchu.peerbanhelper.downloader.DownloaderManagerImpl;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.monitor.ActiveMonitoringModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.AccessHistoryDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.BanHistoryDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.PeerInfoDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.TorrentEntityDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.URLUtil;
import com.ghostchu.peerbanhelper.util.dns.DNSLookup;
import com.ghostchu.peerbanhelper.util.ipdb.IPDB;
import com.ghostchu.peerbanhelper.util.ipdb.IPDBManager;
import com.ghostchu.peerbanhelper.util.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.util.lab.Laboratory;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import com.ghostchu.peerbanhelper.util.query.PBHPage;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.google.common.net.HostAndPort;
import io.javalin.http.Context;
import io.javalin.openapi.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@Slf4j
public final class PBHPeerController extends AbstractFeatureModule {
    private final JavalinWebContainer javalinWebContainer;
    private final HistoryService historyDao;
    private final PeerRecordService peerRecordDao;
    private final DNSLookup dnsLookup;
    private final DownloaderManagerImpl downloaderManager;
    private final TorrentService torrentDao;
    private final IPDBManager iPDBManager;
    private final BtnNetwork btnNetwork;

    public PBHPeerController(JavalinWebContainer javalinWebContainer,
                             HistoryService historyDao, PeerRecordService peerRecordDao,
                             ActiveMonitoringModule activeMonitoringModule,
                             Laboratory laboratory, DNSLookup dnsLookup, DownloaderManagerImpl downloaderManager,
                             TorrentService torrentDao, IPDBManager iPDBManager,
                             @Autowired(required = false) BtnNetwork btnNetwork) {
        super();
        this.javalinWebContainer = javalinWebContainer;
        this.historyDao = historyDao;
        this.peerRecordDao = peerRecordDao;
        this.dnsLookup = dnsLookup;
        this.downloaderManager = downloaderManager;
        this.torrentDao = torrentDao;
        this.iPDBManager = iPDBManager;
        this.btnNetwork = btnNetwork; // TODO: 测试禁用的情况下的依赖注入
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "Peer Controller";
    }

    @Override
    public @NotNull String getConfigName() {
        return "peer-controller";
    }

    @Override
    public void onEnable() {
        javalinWebContainer.routes()
                .get("/api/peer/{ip}", this::handleInfo, Role.USER_READ)
                .get("/api/peer/{ip}/accessHistory", this::handleAccessHistory, Role.USER_READ, Role.PBH_PLUS)
                .get("/api/peer/{ip}/banHistory", this::handleBanHistory, Role.USER_READ, Role.PBH_PLUS)
                .get("/api/peer/{ip}/btnQuery", this::handleBtnQuery, Role.USER_READ)
                .get("/api/peer/{ip}/btnQueryIframe", this::handleBtnQueryIFrame, Role.USER_READ);

    }

    @OpenApi(
            path = "/api/peer/{ip}/btnQueryIframe",
            methods = HttpMethod.GET,
            summary = "BTN 查询 IFrame 页面",
            description = "获取指定 Peer 的 BTN 查询 IFrame 链接",
            tags = {"Peer 管理"},
            pathParams = {
                    @OpenApiParam(name = "ip", description = "Peer 的 IP 地址", required = true)
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleBtnQueryIFrame"
    )
    private void handleBtnQueryIFrame(@NotNull Context ctx) {
        HostAndPort hostAndPort = HostAndPort.fromString(ctx.pathParam("ip"));
        var ipAddress = IPAddressUtil.getIPAddress(hostAndPort.getHost());
        String ip = ipAddress.toCompressedString();
        if (btnNetwork == null) {
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.BTN_NETWORK_NOT_ENABLED), null));
            return;
            /**/
        }
        var ability = btnNetwork.getAbilities().get(BtnAbilityIpQuery.class);
        if (ability == null) {
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.BTN_ABILITY_IP_QUERY_NOT_PROVIDED), null));
            return;
        }
        BtnAbilityIpQuery queryAbility = (BtnAbilityIpQuery) ability;
        var url = URLUtil.appendUrl(queryAbility.getIframeEndpoint(), Map.of(
                "ip", ip,
                "appId", btnNetwork.getAppId(),
                "appSecret", btnNetwork.getAppSecret(),
                "hardwareId", btnNetwork.getBtnHardwareId(),
                "installationId", btnNetwork.getInstallationId()
        ));
        ctx.json(new StdResp(true, null, url));
    }

    @OpenApi(
            path = "/api/peer/{ip}/btnQuery",
            methods = HttpMethod.GET,
            summary = "查询 BTN 封禁状态",
            description = "查询指定 Peer 在 BTN 网络中的封禁状态",
            tags = {"Peer 管理"},
            pathParams = {
                    @OpenApiParam(name = "ip", description = "Peer 的 IP 地址", required = true)
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleBtnQuery"
    )
    private void handleBtnQuery(@NotNull Context ctx) throws IOException {
        // 转换 IP 格式到 PBH 统一内部格式
        HostAndPort hostAndPort = HostAndPort.fromString(ctx.pathParam("ip"));
        var ipAddress = IPAddressUtil.getIPAddress(hostAndPort.getHost());
        String ip = ipAddress.toCompressedString();

        if (btnNetwork == null) {
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.BTN_NETWORK_NOT_ENABLED), null));
            return;
        }
        var ability = btnNetwork.getAbilities().get(BtnAbilityIpQuery.class);
        if (ability == null) {
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.BTN_ABILITY_IP_QUERY_NOT_PROVIDED), null));
            return;
        }
        BtnAbilityIpQuery queryAbility = (BtnAbilityIpQuery) ability;
        var query = queryAbility.query(ip);
        ctx.json(new StdResp(true, null, query));
    }

    @OpenApi(
            path = "/api/peer/{ip}",
            methods = HttpMethod.GET,
            summary = "获取 Peer 信息",
            description = "获取指定 Peer 的基础信息、地理位置和统计数据",
            tags = {"Peer 管理"},
            pathParams = {
                    @OpenApiParam(name = "ip", description = "Peer 的 IP 地址", required = true)
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleInfo"
    )
    private void handleInfo(Context ctx) {
        // 转换 IP 格式到 PBH 统一内部格式
        HostAndPort hostAndPort = HostAndPort.fromString(ctx.pathParam("ip"));
        var ipAddress = IPAddressUtil.getIPAddress(hostAndPort.getHost());
        InetAddress inet = ipAddress.toInetAddress();
        long banCount = historyDao.countHistoriesByIp(inet);
        long torrentAccessCount = peerRecordDao.countRecordsByIp(inet);

        IPAddressTotalTraffic upDownResult = peerRecordDao.queryAddressTotalTraffic(inet);
        long uploadedToPeer = upDownResult.getTotalUploaded();
        long downloadedFromPeer = upDownResult.getTotalDownloaded();
        IPAddressTimeSeen timeSeen = peerRecordDao.queryAddressTimeSeen(inet);
        OffsetDateTime firstTimeSeen = timeSeen.getFirstTimeSeen();
        OffsetDateTime lastTimeSeen = timeSeen.getLastTimeSeen();
        IPDB ipdb = iPDBManager.getIpdb();
        IPGeoData geoIP = null;
        try {
            if (ipdb != null) {
                geoIP = ipdb.query(ipAddress.toInetAddress());
            }
        } catch (Exception e) {
            log.warn("Unable to perform GeoIP query for ip {}", inet);
        }
        String ptrLookup = null;
        try {
            //if (laboratory.isExperimentActivated(Experiments.DNSJAVA.getExperiment())) {
            ptrLookup = dnsLookup.ptr(inet.getHostAddress()).get(3, TimeUnit.SECONDS).orElse(null);
//            } else {
//                ptrLookup = CompletableFuture.supplyAsync(() -> ipAddress.toInetAddress().getCanonicalHostName()).get(3, TimeUnit.SECONDS);
//            }
        } catch (Exception ignored) {
        }
        boolean btnQueryAvailable = false;
        if (btnNetwork != null) {
            var ability = btnNetwork.getAbilities().get(BtnAbilityIpQuery.class);
            if (ability != null) {
                btnQueryAvailable = true;
            }
        }
        var info = new PeerInfoDTO(
                banCount > 0 || torrentAccessCount > 0,
                inet.getHostAddress(), firstTimeSeen, lastTimeSeen, banCount, torrentAccessCount,
                uploadedToPeer, downloadedFromPeer, geoIP, ptrLookup, btnQueryAvailable);
        ctx.json(new StdResp(true, null, info));
    }


    @OpenApi(
            path = "/api/peer/{ip}/banHistory",
            methods = HttpMethod.GET,
            summary = "获取 Peer 封禁历史",
            description = "分页获取指定 Peer 的封禁历史记录",
            tags = {"Peer 管理"},
            pathParams = {
                    @OpenApiParam(name = "ip", description = "Peer 的 IP 地址", required = true)
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "peerBanHistory"
    )
    private void handleBanHistory(Context ctx) {
        InetAddress ip = IPAddressUtil.getIPAddress(ctx.pathParam("ip")).toInetAddress();
        Pageable pageable = new Pageable(ctx);
        Orderable orderBy = new Orderable(Map.of("ban_at", false), ctx)
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
                .addRemapping("description", "description");
        IPage<HistoryEntity> page = historyDao.queryBanHistoryByIp(pageable.toPage(), ip, orderBy);
        page.convert(entity -> {
            TorrentEntityDTO torrentEntityDTO = TorrentEntityDTO.from(torrentDao.getById(entity.getTorrentId()));
            return new BanHistoryDTO(
                    entity.getBanAt(),
                    entity.getUnbanAt(),
                    entity.getIp().getHostAddress(),
                    entity.getPort(),
                    entity.getPeerId(),
                    entity.getPeerClientName(),
                    entity.getPeerUploaded(),
                    entity.getPeerDownloaded(),
                    entity.getPeerProgress(),
                    torrentEntityDTO.infoHash(),
                    torrentEntityDTO.name(),
                    torrentEntityDTO.size(),
                    entity.getModuleName(),
                    tl(locale(ctx), entity.getRuleName()),
                    tl(locale(ctx), entity.getDescription()),
                    downloaderManager.getDownloadInfo(entity.getDownloader())
            );
        });
        ctx.json(new StdResp(true, null, PBHPage.from(page)));
    }


    @OpenApi(
            path = "/api/peer/{ip}/accessHistory",
            methods = HttpMethod.GET,
            summary = "获取 Peer 访问历史",
            description = "分页获取指定 Peer 的访问历史记录",
            tags = {"Peer 管理"},
            pathParams = {
                    @OpenApiParam(name = "ip", description = "Peer 的 IP 地址", required = true)
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleAccessHistory"
    )
    private void handleAccessHistory(Context ctx) {
        InetAddress ip = IPAddressUtil.getIPAddress(ctx.pathParam("ip")).toInetAddress();
        Pageable pageable = new Pageable(ctx);
        Orderable orderBy = new Orderable(Map.of("last_time_seen", false, "address", false, "port", true), ctx)
                .addRemapping("peerId", "peer_id")
                .addRemapping("clientName", "client_name")
                .addRemapping("firstTimeSeen", "first_time_seen")
                .addRemapping("lastTimeSeen", "last_time_seen");
        IPage<PeerRecordEntity> page = peerRecordDao.queryAccessHistoryByIp(pageable.toPage(), ip, orderBy);
        IPage<AccessHistoryDTO> accessHistoryDTOIPage = page.convert(entity -> new AccessHistoryDTO(
                entity.getId(),
                entity.getAddress().getHostAddress(),
                entity.getPort(),
                TorrentEntityDTO.from(torrentDao.getById(entity.getTorrentId())),
                downloaderManager.getDownloadInfo(entity.getDownloader()),
                entity.getPeerId(),
                entity.getClientName(),
                entity.getUploaded(),
                entity.getUploadedOffset(),
                entity.getUploadSpeed(),
                entity.getDownloaded(),
                entity.getDownloadedOffset(),
                entity.getDownloadSpeed(),
                entity.getLastFlags(),
                entity.getFirstTimeSeen(),
                entity.getLastTimeSeen()

        ));
        ctx.json(new StdResp(true, null, PBHPage.from(accessHistoryDTOIPage)));
    }


    @Override
    public void onDisable() {

    }

}
