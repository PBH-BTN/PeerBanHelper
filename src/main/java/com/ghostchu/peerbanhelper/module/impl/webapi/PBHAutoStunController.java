package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderManager;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.*;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.query.Page;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.ghostchu.peerbanhelper.util.traversal.btstun.BTStunInstance;
import com.ghostchu.peerbanhelper.util.traversal.btstun.BTStunManager;
import com.ghostchu.peerbanhelper.util.traversal.btstun.StunManager;
import com.ghostchu.peerbanhelper.util.traversal.forwarder.ForwarderIOHandlerType;
import com.ghostchu.peerbanhelper.util.traversal.forwarder.table.ConnectionStatistics;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;

import java.net.InetSocketAddress;
import java.util.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@Slf4j
public class PBHAutoStunController extends AbstractFeatureModule {
    private final JavalinWebContainer javalinWebContainer;
    private final StunManager stunManager;
    private final BTStunManager bTStunManager;
    private final DownloaderManager downloaderManager;
    private final SystemInfo systemInfo;

    public PBHAutoStunController(JavalinWebContainer javalinWebContainer, StunManager stunManager, BTStunManager bTStunManager, DownloaderManager downloaderManager, SystemInfo systemInfo) {
        super();
        this.javalinWebContainer = javalinWebContainer;
        this.stunManager = stunManager;
        this.bTStunManager = bTStunManager;
        this.downloaderManager = downloaderManager;
        this.systemInfo = systemInfo;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - AutoSTUN";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webeapi-autostun";
    }

    @Override
    public void onEnable() {
        javalinWebContainer.javalin()
                .get("/api/autostun/status", this::getModuleStatus, Role.USER_READ)
                .post("/api/autostun/refreshNatType", this::refreshNatType, Role.USER_WRITE)
                .post("/api/autostun/restart", this::autoStunRestart, Role.USER_WRITE)
                .get("/api/autostun/tunnels", this::tunnels, Role.USER_READ)
                .get("/api/autostun/tunnel/{downloader}/info", this::tunnelInfo, Role.USER_READ)
                .get("/api/autostun/tunnel/{downloader}/connections", this::tunnelConnections, Role.USER_READ)
                .put("/api/autostun/config", this::putModuleConfig, Role.USER_WRITE);
    }

    private void getModuleStatus(@NotNull Context context) {
        var section = Main.getMainConfig().getConfigurationSection("auto-stun");
        if (section == null) throw new IllegalStateException("Auto-stun configuration section not found");
        var defaultIpv4Gateway = systemInfo.getOperatingSystem().getNetworkParams().getIpv4DefaultGateway();
        boolean isDockerBridge =
                defaultIpv4Gateway.startsWith("172.")
                        && ExternalSwitch.parse("pbh.release", "unknown").toLowerCase(Locale.ROOT).contains("docker")
                        && ExternalSwitch.parseBoolean("pbh.autostun.checkdockerbridge", true);
        AutoStunConfigDto autoStunConfigDto = new AutoStunConfigDto(
                section.getBoolean("enabled", false),
                section.getBoolean("use-friendly-loopback-mapping", true),
                section.getStringList("downloaders").stream()
                        .map(downloaderManager::getDownloaderById)
                        .filter(Objects::nonNull)
                        .map(downloaderManager::getDownloadInfo).toList(),
                stunManager.getCachedNatType(), isDockerBridge);
        context.json(new StdResp(true, null, autoStunConfigDto));
    }

    private void putModuleConfig(@NotNull Context context) throws Exception {
        AutoStunConfigForm autoStunConfigForm = context.bodyAsClass(AutoStunConfigForm.class);
        var section = Main.getMainConfig().getConfigurationSection("auto-stun");
        if (section == null) throw new IllegalStateException("Auto-stun configuration section not found");
        section.set("enabled", autoStunConfigForm.isEnabled());
        section.set("use-friendly-loopback-mapping", autoStunConfigForm.isUseFriendlyLoopbackMapping());
        section.set("downloaders", autoStunConfigForm.getDownloaders());
        Main.getMainConfig().save(Main.getMainConfigFile());
        bTStunManager.reloadModule();
        context.json(new StdResp(true, tl(locale(context), Lang.AUTOSTUN_CONFIG_REAPPLIED), null));
    }

    private void tunnelConnections(@NotNull Context context) {
        Pageable pageable = new Pageable(context);
        var downloaderId = context.pathParam("downloader");
        var downloader = downloaderManager.getDownloaderById(downloaderId);
        if (downloader == null) {
            context.json(new StdResp(false, tl(locale(context), new TranslationComponent(Lang.AUTOSTUN_DOWNLOADER_NOT_EXISTS, downloaderId)), null));
            return;
        }
        var stunInstance = bTStunManager.getStunInstance(downloader);
        if (stunInstance == null) {
            context.json(new StdResp(false, tl(locale(context), new TranslationComponent(Lang.AUTOSTUN_DOWNLOADER_TUNNEL_NOT_EXISTS, downloader.getName())), null));
            return;
        }
        var forwarder = stunInstance.getTcpForwarder();
        if (forwarder == null) {
            context.json(new StdResp(false, tl(locale(context), new TranslationComponent(Lang.AUTOSTUN_DOWNLOADER_TUNNEL_FORWARDER_NOT_EXISTS, downloader.getName())), List.of()));
            return;
        }
        List<TunnelProxyConnectionDTO> connectionList = new LinkedList<>();
        for (Map.Entry<InetSocketAddress, InetSocketAddress> connection : forwarder.getDownstreamAddressAsKeyConnectionMap().entrySet()) {
            var downstreamAddr = connection.getKey();
            var proxyLAddr = connection.getValue();
            ConnectionStatistics statistics = forwarder.getDownstreamAddressAsKeyConnectionStats().get(downstreamAddr);
            if (statistics == null) continue; // disconnected during getting
            TunnelProxyConnectionDTO tunnelProxyConnectionDTO = new TunnelProxyConnectionDTO(
                    statistics.getIpGeoData(),
                    downstreamAddr.getHostString(),
                    downstreamAddr.getPort(),
                    forwarder.getProxyHost(),
                    forwarder.getProxyPort(),
                    proxyLAddr.getHostString(),
                    proxyLAddr.getPort(),
                    forwarder.getUpstremHost(),
                    forwarder.getUpstreamPort(),
                    statistics.getEstablishedAt(),
                    statistics.getLastActivityAt(),
                    statistics.getToDownstreamBytes().sum(),
                    statistics.getToUpstreamBytes().sum()
            );
            connectionList.add(tunnelProxyConnectionDTO);
        }
        //connectionList.sort(Comparator.comparing(TunnelProxyConnectionDTO::getToDownstreamBytes).reversed());
        //order connections by  toDownstreamBytes and toUpstreamBytes
        connectionList.sort(Comparator.comparing(TunnelProxyConnectionDTO::getToDownstreamBytes)
                .thenComparing(TunnelProxyConnectionDTO::getToUpstreamBytes)
                .reversed());
        long total = connectionList.size();
        long skip = pageable.getZeroBasedPage() * pageable.getSize();
        context.json(new StdResp(true, null, new Page<>(pageable, total, connectionList.stream().skip(skip).limit(pageable.getSize()).toList())));
    }

    private void tunnelInfo(@NotNull Context context) {
        var downloaderId = context.pathParam("downloader");
        var downloader = downloaderManager.getDownloaderById(downloaderId);
        if (downloader == null) {
            context.json(new StdResp(false, tl(locale(context), new TranslationComponent(Lang.AUTOSTUN_DOWNLOADER_NOT_EXISTS, downloaderId)), null));
            return;
        }
        var stunInstance = bTStunManager.getStunInstance(downloader);
        if (stunInstance == null) {
            context.json(new StdResp(false, tl(locale(context), new TranslationComponent(Lang.AUTOSTUN_DOWNLOADER_TUNNEL_NOT_EXISTS, downloader.getName())), null));
            return;
        }
        context.json(new StdResp(true, null, toTunnelInfoDto(locale(context), stunInstance)));
    }

    private void tunnels(@NotNull Context context) {
        List<TunnelsDTO> tunnels = new ArrayList<>();
        for (Map.Entry<Downloader, BTStunInstance> entry : bTStunManager.getDownloadStunInstances().entrySet()) {
            var downloader = entry.getKey();
            var stunInstance = entry.getValue();
            tunnels.add(new TunnelsDTO(downloaderManager.getDownloadInfo(downloader), toTunnelInfoDto(locale(context), stunInstance)));
        }
        context.json(new StdResp(true, null, tunnels));
    }

    private TunnelInfoDTO toTunnelInfoDto(String locale, BTStunInstance stunInstance) {
        return new TunnelInfoDTO(
                stunInstance.getTunnel() != null && stunInstance.getTunnel().isValid(),
                stunInstance.getTunnel() != null ? stunInstance.getTunnel().getStartedAt() : 0,
                stunInstance.getTunnel() != null ? stunInstance.getTunnel().getLastSuccessHeartbeatAt() : 0,
                stunInstance.getTcpForwarder() != null ? stunInstance.getTcpForwarder().getConnectionHandled() : 0,
                stunInstance.getTcpForwarder() != null ? stunInstance.getTcpForwarder().getConnectionFailed() : 0,
                stunInstance.getTcpForwarder() != null ? stunInstance.getTcpForwarder().getConnectionBlocked() : 0,
                stunInstance.getTcpForwarder() != null ? stunInstance.getTcpForwarder().getConnectionRejected() : 0,
                stunInstance.getTcpForwarder() != null ? stunInstance.getTcpForwarder().getTotalToDownstream() : 0,
                stunInstance.getTcpForwarder() != null ? stunInstance.getTcpForwarder().getTotalToUpstream() : 0,
                stunInstance.getTcpForwarder() != null ? stunInstance.getTcpForwarder().getEstablishedConnections() : 0,
                stunInstance.getTcpForwarder() != null ? stunInstance.getTcpForwarder().getProxyHost() : "???",
                stunInstance.getTcpForwarder() != null ? stunInstance.getTcpForwarder().getProxyPort() : 0,
                stunInstance.getTcpForwarder() != null ? stunInstance.getTcpForwarder().getUpstremHost() : "???",
                stunInstance.getTcpForwarder() != null ? stunInstance.getTcpForwarder().getUpstreamPort() : 0,
                stunInstance.getTcpForwarder() != null ? stunInstance.getTcpForwarder().getForwarderIOHandlerType() : ForwarderIOHandlerType.DEFAULT,
                tl(locale, stunInstance.getShutdownReason())
        );
    }

    private void autoStunRestart(@NotNull Context context) {
        try {
            bTStunManager.reloadModule();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        context.json(new StdResp(true, tl(locale(context), new TranslationComponent(Lang.AUTOSTUN_RESTARTED)), null));
    }

    private void refreshNatType(@NotNull Context context) {
        Thread.ofVirtual().name("Refresh NAT Status").start(stunManager::refreshNatType);
        context.json(new StdResp(true, "Refreshing NAT Status", null));
    }

    @Override
    public void onDisable() {

    }

    private void status(@NotNull Context context) {

    }


}
