package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ability.impl.BtnAbilityIpQuery;
import com.ghostchu.peerbanhelper.databasent.dto.AccessHistoryDTO;
import com.ghostchu.peerbanhelper.databasent.dto.BanHistoryDTO;
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
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.sql.SQLException;
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
    private final ActiveMonitoringModule activeMonitoringModule;
    private final Laboratory laboratory;
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
        this.activeMonitoringModule = activeMonitoringModule;
        this.laboratory = laboratory;
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
        javalinWebContainer.javalin()
                .get("/api/peer/{ip}", this::handleInfo, Role.USER_READ)
                .get("/api/peer/{ip}/accessHistory", this::handleAccessHistory, Role.USER_READ, Role.PBH_PLUS)
                .get("/api/peer/{ip}/banHistory", this::handleBanHistory, Role.USER_READ, Role.PBH_PLUS)
                .get("/api/peer/{ip}/btnQuery", this::handleBtnQuery, Role.USER_READ)
                .get("/api/peer/{ip}/btnQueryIframe", this::handleBtnQueryIFrame, Role.USER_READ);

    }

    private void handleBtnQueryIFrame(@NotNull Context ctx) {
        HostAndPort hostAndPort = HostAndPort.fromString(ctx.pathParam("ip"));
        var ipAddress = IPAddressUtil.getIPAddress(hostAndPort.getHost());
        String ip = ipAddress.toNormalizedString();
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

    private void handleBtnQuery(@NotNull Context ctx) throws IOException {
        // 转换 IP 格式到 PBH 统一内部格式
        HostAndPort hostAndPort = HostAndPort.fromString(ctx.pathParam("ip"));
        var ipAddress = IPAddressUtil.getIPAddress(hostAndPort.getHost());
        String ip = ipAddress.toNormalizedString();

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


    private void handleBanHistory(Context ctx) throws SQLException {
        InetAddress ip = IPAddressUtil.getIPAddress(ctx.pathParam("ip")).toInetAddress();
        Pageable pageable = new Pageable(ctx);
        Orderable orderBy = new Orderable(Map.of("banAt", false), ctx);
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
                    entity.getRuleName(),
                    tl(locale(ctx), entity.getDescription()),
                    downloaderManager.getDownloadInfo(entity.getDownloader())
            );
        });
        ctx.json(new StdResp(true, null, PBHPage.from(page)));
    }


    private void handleAccessHistory(Context ctx) {
        InetAddress ip = IPAddressUtil.getIPAddress(ctx.pathParam("ip")).toInetAddress();
        Pageable pageable = new Pageable(ctx);
        Orderable orderBy = new Orderable(Map.of("lastTimeSeen", false, "address", false, "port", true), ctx);
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
