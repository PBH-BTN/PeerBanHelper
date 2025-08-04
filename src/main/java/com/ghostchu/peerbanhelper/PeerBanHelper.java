package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.impl.tmp.TrackedSwarmDao;
import com.ghostchu.peerbanhelper.downloader.DownloaderManager;
import com.ghostchu.peerbanhelper.event.PBHServerStartedEvent;
import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.gui.TaskbarState;
import com.ghostchu.peerbanhelper.module.ModuleManager;
import com.ghostchu.peerbanhelper.module.impl.ai.mcp.MCPController;
import com.ghostchu.peerbanhelper.module.impl.background.BackgroundModule;
import com.ghostchu.peerbanhelper.module.impl.monitor.ActiveMonitoringModule;
import com.ghostchu.peerbanhelper.module.impl.monitor.SwarmTrackingModule;
import com.ghostchu.peerbanhelper.module.impl.rule.*;
import com.ghostchu.peerbanhelper.module.impl.webapi.*;
import com.ghostchu.peerbanhelper.platform.win32.workingset.jna.WorkingSetManagerFactory;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.*;
import com.ghostchu.peerbanhelper.util.ipdb.IPDB;
import com.ghostchu.peerbanhelper.util.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.util.traversal.btstun.StunManager;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.net.InetAddresses;
import io.javalin.util.JavalinBindException;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public class PeerBanHelper implements Reloadable {
    @Autowired
    private TrackedSwarmDao trackerPeersDao;
    @Autowired
    @Getter
    private DownloaderManager downloaderManager;
    @Autowired
    @Getter
    private DownloaderServerImpl downloaderServer;
    private final Cache<String, IPDBResponse> geoIpCache = CacheBuilder.newBuilder()
            .expireAfterAccess(ExternalSwitch.parseInt("pbh.geoIpCache.timeout", 300000), TimeUnit.MILLISECONDS)
            .maximumSize(ExternalSwitch.parseInt("pbh.geoIpCache.size", 300))
            .softValues()
            .build();
    @Getter
    private int httpdPort;
    @Autowired
    private Database databaseManager;
    @Autowired
    private ModuleManager moduleManager;
    @Getter
    @Nullable
    private IPDB ipdb = null;
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private AlertManager alertManager;
    @Autowired
    private CrashManager crashManager;
    @Autowired
    private HTTPUtil httpUtil;
    @Autowired
    private PBHPortMapper pBHPortMapper;
    @Autowired
    private StunManager bTStunManager;
    @Autowired
    private DownloaderDiscovery downloaderDiscovery;


    public PeerBanHelper() {
        reloadConfig();
    }

    private void reloadConfig() {
        this.httpdPort = ExternalSwitch.parseInt("pbh.port", Main.getMainConfig().getInt("server.http"));
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }


    public void start() {
        checkKnownCrashes();
        setupIPDB();
        registerHttpServer();
        if (webContainer.getToken() == null || webContainer.getToken().isBlank()) {
            for (int i = 0; i < 50; i++) {
                log.error(tlUI(Lang.PBH_OOBE_REQUIRED, "http://127.0.0.1:" + webContainer.javalin().port()));
            }
            Main.getGuiManager().openUrlInBrowser("http://127.0.0.1:" + webContainer.javalin().port());
        }
        Main.getReloadManager().register(this);
        postCompatibilityCheck();
        registerModules();
        sendSnapshotAlert();
        downloaderServer.load();
        Main.getGuiManager().taskbarControl().updateProgress(null, TaskbarState.OFF, 0.0f);
        crashManager.putRunningFlag();
        Main.getEventBus().post(new PBHServerStartedEvent());
        Main.getGuiManager().onPBHFullyStarted(this);
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        System.gc();
        Thread.startVirtualThread(() -> {
            if (os.startsWith("win")) {
                WorkingSetManagerFactory.trimMemory();
            }
        });
        runTestCode();
    }

    private void checkKnownCrashes() {
        crashManager.checkCrashRecovery();
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.startsWith("win") && Main.getGuiManager().isGuiAvailable()) {
            // Fuck awt.dll, nobody care desktop users, told user to use JBR for patch that we really need.v
            var bean = ManagementFactory.getRuntimeMXBean();
            if (!bean.getVmVendor().contains("JetBrains")) {
                if (!alertManager.identifierAlertExistsIncludeRead("incompatibility-jre-windows-liberica")) {
                    alertManager.publishAlert(false, AlertLevel.WARN,
                            "incompatibility-jre-windows-liberica",
                            new TranslationComponent(Lang.JBR_REQUIRED_TITLE),
                            new TranslationComponent(Lang.JBR_REQUIRED_DESCRIPTION, bean.getVmVendor(), bean.getVmVersion()));
                    Main.getGuiManager().createDialog(Level.WARN, tlUI(new TranslationComponent(Lang.JBR_REQUIRED_TITLE)),
                            tlUI(new TranslationComponent(Lang.JBR_REQUIRED_DESCRIPTION, bean.getVmVendor(), bean.getVmVersion())),
                            () -> {
                            });
                }
            }
        }


//        if (!crashManager.isRunningFlagExists()) return;
//        Main.getGuiManager().createDialog(Level.WARN, tlUI(Lang.CRASH_MANAGER_TITLE), tlUI(Lang.CRASH_MANAGER_DESCRIPTION), () -> {
//            if ("SWING".equals(Main.getGuiManager().getName())) {
//                String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
//                if (os.startsWith("win")) {
//                    Main.getGuiManager().createYesNoDialog(Level.INFO, tlUI(Lang.CRASH_MANAGER_GUI_RELATED_TITLE), tlUI(Lang.CRASH_MANAGER_GUI_RELATED_DESCRIPTION),
//                            () -> {
//                                Main.getMainConfig().set("gui", "swt");
//                                try {
//                                    Main.getMainConfig().save(Main.getMainConfigFile());
//                                    System.exit(0);
//                                } catch (IOException e) {
//                                    Main.getGuiManager().createDialog(Level.ERROR, "Unable to save configuration", e.getMessage(), () -> {
//                                    });
//                                }
//                            }, null
//                    );
//                }
//            }
//        });
    }

    private void postCompatibilityCheck() {
        if (ExternalSwitch.parseBoolean("pbh.forceBitnessCheckFail")) {
            ExchangeMap.UNSUPPORTED_PLATFORM = true;
            ExchangeMap.GUI_DISPLAY_FLAGS.add(new ExchangeMap.DisplayFlag("unsupported-platform", 10, tlUI(Lang.TITLE_INCOMPATIBLE_PLATFORM)));
            log.warn(tlUI(Lang.INCOMPATIBLE_BITNESS_LOG));
            if (!alertManager.identifierAlertExistsIncludeRead("incomaptible-bitness")) {
                alertManager.publishAlert(false, AlertLevel.WARN, "incomaptible-bitness", new TranslationComponent(Lang.INCOMPATIBLE_BITNESS_TITLE), new TranslationComponent(Lang.INCOMPATIBLE_BITNESS_DESCRIPTION));
                Main.getGuiManager().createNotification(Level.WARN, tlUI(Lang.INCOMPATIBLE_BITNESS_TITLE), tlUI(Lang.INCOMPATIBLE_BITNESS_DESCRIPTION));
            }
        }
        if (ExternalSwitch.parseBoolean("pbh.app-v")) {
            ExchangeMap.GUI_DISPLAY_FLAGS.add(new ExchangeMap.DisplayFlag("app-v", 10, tlUI(Lang.TITLE_APP_V_CONTAINER)));
        }
    }

    @SneakyThrows
    private void runTestCode() {
        if (!Main.getMeta().isSnapshotOrBeta() && !"LiveDebug".equalsIgnoreCase(ExternalSwitch.parse("pbh.release"))) {
            return;
        }
        ExchangeMap.GUI_DISPLAY_FLAGS.add(new ExchangeMap.DisplayFlag("debug-mode", 20, tlUI(Lang.GUI_TITLE_DEBUG)));
    }


    private void sendSnapshotAlert() {
        if (Main.getMeta().isSnapshotOrBeta()) {
            alertManager.publishAlert(false, AlertLevel.INFO, "unstable-alert", new TranslationComponent(Lang.ALERT_SNAPSHOT), new TranslationComponent(Lang.ALERT_SNAPSHOT_DESCRIPTION));
        } else {
            alertManager.markAlertAsRead("unstable-alert");
        }
    }


    private void setupIPDB() {
        try {
            String accountId = Main.getMainConfig().getString("ip-database.account-id", "");
            String licenseKey = Main.getMainConfig().getString("ip-database.license-key", "");
            String databaseCity = Main.getMainConfig().getString("ip-database.database-city", "GeoLite2-City");
            String databaseASN = Main.getMainConfig().getString("ip-database.database-asn", "GeoLite2-ASN");
            boolean autoUpdate = Main.getMainConfig().getBoolean("ip-database.auto-update");
            this.ipdb = new IPDB(new File(Main.getDataDirectory(), "ipdb"), accountId, licenseKey,
                    databaseCity, databaseASN, autoUpdate, Main.getUserAgent(), httpUtil);
        } catch (Exception e) {
            log.info(tlUI(Lang.IPDB_INVALID),e );
        }
    }


    public void shutdown() {
        // place some clean code here
        downloaderServer.close();
        this.moduleManager.unregisterAll();
        this.databaseManager.close();
        if (this.ipdb != null) {
            this.ipdb.close();
        }
        try {
            downloaderManager.close();
        } catch (Exception e) {
            log.warn("Unable to safe shutdown downloader manager", e);
        }
        Main.getReloadManager().unregister(this);
        log.info(tlUI(Lang.SHUTDOWN_DONE));
    }


    private void registerHttpServer() {
        String token = ExternalSwitch.parse("pbh.apiToken", Main.getMainConfig().getString("server.token"));
        String host = ExternalSwitch.parse("pbh.serverAddress", Main.getMainConfig().getString("server.address"));
        if (host.equals("0.0.0.0") || host.equals("::") || host.equals("localhost")) {
            host = null;
        }
        try {
            webContainer.start(host, httpdPort, token);
        } catch (JavalinBindException e) {
            if (e.getMessage().contains("Port already in use")) {
                log.error(tlUI(Lang.JAVALIN_PORT_IN_USE, httpdPort));
                throw new JavalinBindException(tlUI(Lang.JAVALIN_PORT_IN_USE, httpdPort), e);
            } else if (e.getMessage().contains("require elevated privileges")) {
                log.error(tlUI(Lang.JAVALIN_PORT_REQUIRE_PRIVILEGES));
                throw new JavalinBindException(tlUI(Lang.JAVALIN_PORT_REQUIRE_PRIVILEGES, httpdPort), e);
            }
        }
    }

    /**
     * 注册 Modules
     */
    private void registerModules() {
        log.info(tlUI(Lang.WAIT_FOR_MODULES_STARTUP));
        moduleManager.register(PBHBackgroundTaskController.class);
        moduleManager.register(PBHGeneralController.class);
        moduleManager.register(IPBlackList.class);
        moduleManager.register(PeerIdBlacklist.class);
        moduleManager.register(ClientNameBlacklist.class);
        moduleManager.register(ExpressionRule.class);
        moduleManager.register(ProgressCheatBlocker.class);
        moduleManager.register(MultiDialingBlocker.class);
        moduleManager.register(AutoRangeBan.class);
        moduleManager.register(BtnNetworkOnline.class);
        moduleManager.register(BlockListController.class);
        moduleManager.register(IPBlackRuleList.class);
        moduleManager.register(PTRBlacklist.class);
        moduleManager.register(PBHMetricsController.class);
        moduleManager.register(PBHBanController.class);
        moduleManager.register(PBHMetadataController.class);
        moduleManager.register(PBHDownloaderController.class);
        moduleManager.register(RuleSubController.class);
        moduleManager.register(PBHAuthenticateController.class);
        moduleManager.register(ActiveMonitoringModule.class);
        moduleManager.register(PBHPlusController.class);
        moduleManager.register(PBHOOBEController.class);
        moduleManager.register(PBHChartController.class);
        moduleManager.register(PBHTorrentController.class);
        moduleManager.register(PBHPeerController.class);
        moduleManager.register(PBHAlertController.class);
        moduleManager.register(PBHLogsController.class);
        moduleManager.register(PBHPushController.class);
        moduleManager.register(PBHLabController.class);
        moduleManager.register(PBHEasterEggController.class);
        moduleManager.register(PBHUtilitiesController.class);
        moduleManager.register(BackgroundModule.class);
        moduleManager.register(SwarmTrackingModule.class);
        moduleManager.register(MCPController.class);
        moduleManager.register(PBHAutoStunController.class);
    }

    public IPDBResponse queryIPDB(PeerAddress address) {
        try {
            return geoIpCache.get(address.getIp(), () -> {
                if (ipdb == null) {
                    return new IPDBResponse(new LazyLoad<>(() -> null));
                } else {
                    return new IPDBResponse(new LazyLoad<>(() -> {
                        try {
                            return ipdb.query(InetAddresses.forString(address.getIp()));
                        } catch (Exception ignored) {
                            return null;
                        }
                    }));
                }
            });
        } catch (ExecutionException e) {
            return new IPDBResponse(null);
        }
    }

    public String getWebUiUrl() {
        return "http://localhost:" + Main.getServer().getHttpdPort() + "/?token=" + UrlEncoderDecoder.encodePath(webContainer.getToken());
    }


    /**
     * Use @Autowired if available
     *
     * @return JavalinWebContainer
     */
    @Nullable
    public JavalinWebContainer getWebContainer() {
        return webContainer;
    }

    public record IPDBResponse(
            LazyLoad<IPGeoData> geoData
    ) {
    }

}
