package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.dao.impl.BanListDao;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderFeatureFlag;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.BiglyBT;
import com.ghostchu.peerbanhelper.downloader.impl.bitcomet.BitComet;
import com.ghostchu.peerbanhelper.downloader.impl.deluge.Deluge;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.QBittorrent;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.impl.enhanced.QBittorrentEE;
import com.ghostchu.peerbanhelper.downloader.impl.transmission.Transmission;
import com.ghostchu.peerbanhelper.event.LivePeersUpdatedEvent;
import com.ghostchu.peerbanhelper.event.PBHServerStartedEvent;
import com.ghostchu.peerbanhelper.event.PeerBanEvent;
import com.ghostchu.peerbanhelper.event.PeerUnbanEvent;
import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.invoker.BanListInvoker;
import com.ghostchu.peerbanhelper.invoker.impl.CommandExec;
import com.ghostchu.peerbanhelper.invoker.impl.IPFilterInvoker;
import com.ghostchu.peerbanhelper.ipdb.IPDB;
import com.ghostchu.peerbanhelper.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.lab.Experiments;
import com.ghostchu.peerbanhelper.lab.Laboratory;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.module.*;
import com.ghostchu.peerbanhelper.module.impl.background.BackgroundModule;
import com.ghostchu.peerbanhelper.module.impl.rule.*;
import com.ghostchu.peerbanhelper.module.impl.webapi.*;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.*;
import com.ghostchu.peerbanhelper.util.dns.DNSLookup;
import com.ghostchu.peerbanhelper.util.rule.ModuleMatchCache;
import com.ghostchu.peerbanhelper.util.time.ExceptedTime;
import com.ghostchu.peerbanhelper.util.time.TimeoutProtect;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.wrapper.*;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonObject;
import com.j256.ormlite.misc.TransactionManager;
import inet.ipaddr.IPAddress;
import inet.ipaddr.format.util.DualIPv4v6Tries;
import io.javalin.util.JavalinBindException;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.pqc.legacy.math.ntru.util.Util;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.configuration.MemoryConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.sql.SQLException;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public class PeerBanHelperServer implements Reloadable {
    private static final long BANLIST_SAVE_INTERVAL = 60 * 60 * 1000;
    private final CheckResult NO_MATCHES_CHECK_RESULT = new CheckResult(getClass(), PeerAction.NO_ACTION, 0, -1L, -1L, new TranslationComponent("No Matches"), new TranslationComponent("No Matches"));
    private final Map<PeerAddress, BanMetadata> BAN_LIST = new ConcurrentSkipListMap<>();
    @Getter
    private final AtomicBoolean needReApplyBanList = new AtomicBoolean();
    private final Deque<ScheduledBanListOperation> scheduledBanListOperations = new ConcurrentLinkedDeque<>();
    private final List<Downloader> downloaders = new CopyOnWriteArrayList<>();
    @Getter
    private final DualIPv4v6Tries ignoreAddresses = new DualIPv4v6Tries();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    @Getter
    private final List<BanListInvoker> banListInvoker = new ArrayList<>();

    private final Lock banWaveLock = new ReentrantLock();
    private final Cache<String, IPDBResponse> geoIpCache = CacheBuilder.newBuilder()
            .expireAfterAccess(ExternalSwitch.parseInt("pbh.geoIpCache.timeout", 300000), TimeUnit.MILLISECONDS)
            .maximumSize(ExternalSwitch.parseInt("pbh.geoIpCache.size", 300))
            .softValues()
            .build();
    private String pbhServerAddress;
    @Getter
    private long banDuration;
    @Getter
    private int httpdPort;
    @Getter
    private boolean hideFinishLogs;
    @Autowired
    private ModuleMatchCache moduleMatchCache;
    private ScheduledExecutorService BAN_WAVE_SERVICE;
    @Getter
    private Map<PeerAddress, List<PeerMetadata>> LIVE_PEERS = new HashMap<>();
    @Autowired
    @Qualifier("persistMetrics")
    private BasicMetrics metrics;
    @Autowired
    private Database databaseManager;
    @Autowired
    private ModuleManager moduleManager;
    @Getter
    @Nullable
    private IPDB ipdb = null;
    private WatchDog banWaveWatchDog;
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private AlertManager alertManager;
    @Autowired
    private BanListDao banListDao;
    @Autowired
    private Laboratory laboratory;
    @Autowired
    private DNSLookup dnsLookup;
    @Getter
    private boolean globalPaused = false;
//    @Autowired
//    private IPFSBanListShare share;

    public PeerBanHelperServer() {
        reloadConfig();
    }

    private void reloadConfig() {
        this.pbhServerAddress = Main.getPbhServerAddress();
        this.banDuration = Main.getProfileConfig().getLong("ban-duration");
        this.httpdPort = ExternalSwitch.parseInt("pbh.port", Main.getMainConfig().getInt("server.http"));
        this.hideFinishLogs = Main.getMainConfig().getBoolean("logger.hide-finish-log");
        Main.getProfileConfig().getStringList("ignore-peers-from-addresses").forEach(ip -> {
            IPAddress ignored = IPAddressUtil.getIPAddress(ip);
            ignoreAddresses.add(ignored);
        });
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        loadDownloaders();
        resetKnownDownloaders();
        loadBanListToMemory();
        registerTimer();
        unbanWhitelistedPeers();
        return Reloadable.super.reloadModule();
    }

    private void unbanWhitelistedPeers() {
        for (PeerAddress peerAddress : BAN_LIST.keySet()) {
            var node = ignoreAddresses.elementsContaining(peerAddress.getAddress());
            if (node != null) {
                scheduleUnBanPeer(peerAddress);
            }
        }
    }

    public void start() throws SQLException {
        log.info(tlUI(Lang.MOTD, Main.getMeta().getVersion()));
        loadDownloaders();
        registerBanListInvokers();
        registerModules();
        setupIPDB();
        registerHttpServer();
        resetKnownDownloaders();
        loadBanListToMemory();
        registerTimer();
        banListInvoker.forEach(BanListInvoker::reset);
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::saveBanList, 10 * 1000, BANLIST_SAVE_INTERVAL, TimeUnit.MILLISECONDS);
        if (webContainer.getToken() == null || webContainer.getToken().isBlank()) {
            for (int i = 0; i < 50; i++) {
                log.error(tlUI(Lang.PBH_OOBE_REQUIRED, "http://localhost:" + webContainer.javalin().port()));
            }
        }
        Main.getReloadManager().register(this);
        Main.getEventBus().post(new PBHServerStartedEvent(this));
        postCompatibilityCheck();
        sendSnapshotAlert();
        runTestCode();
        Main.getGuiManager().taskbarControl().updateProgress(null, Taskbar.State.OFF, 0.0f);
    }

    private void postCompatibilityCheck() {
        if (!Util.is64BitJVM() || ExternalSwitch.parseBoolean("pbh.forceBitnessCheckFail")) {
            ExchangeMap.UNSUPPORTED_PLATFORM = true;
            ExchangeMap.GUI_DISPLAY_FLAGS.add(new ExchangeMap.DisplayFlag("unsupported-platform", 10, tlUI(Lang.TITLE_INCOMPATIBLE_PLATFORM)));
            log.warn(tlUI(Lang.INCOMPATIBLE_BITNESS_LOG));
            if (!alertManager.identifierAlertExistsIncludeRead("incomaptible-bitness")) {
                alertManager.publishAlert(false, AlertLevel.WARN, "incomaptible-bitness", new TranslationComponent(Lang.INCOMPATIBLE_BITNESS_TITLE), new TranslationComponent(Lang.INCOMPATIBLE_BITNESS_DESCRIPTION));
                Main.getGuiManager().createNotification(Level.WARNING, tlUI(Lang.INCOMPATIBLE_BITNESS_TITLE), tlUI(Lang.INCOMPATIBLE_BITNESS_DESCRIPTION));
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
        // run some junky test code here
//        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
//                .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
//        root.setLevel(ch.qos.logback.classic.Level.TRACE);
    }


    private void sendSnapshotAlert() {
        if (Main.getMeta().isSnapshotOrBeta()) {
            alertManager.publishAlert(false, AlertLevel.INFO, "unstable-alert", new TranslationComponent(Lang.ALERT_SNAPSHOT), new TranslationComponent(Lang.ALERT_SNAPSHOT_DESCRIPTION));
        } else {
            alertManager.markAlertAsRead("unstable-alert");
        }
    }

    public void loadDownloaders() {
        this.downloaders.clear();
        ConfigurationSection clientSection = Main.getMainConfig().getConfigurationSection("client");
        if (clientSection == null) {
            return;
        }
        for (String client : clientSection.getKeys(false)) {
            ConfigurationSection downloaderSection = clientSection.getConfigurationSection(client);
            String endpoint = downloaderSection.getString("endpoint");
            Downloader downloader = createDownloader(client, downloaderSection);
            registerDownloader(downloader);
            log.info(tlUI(Lang.DISCOVER_NEW_CLIENT, downloader.getType(), client, endpoint));
        }
    }

    public Downloader createDownloader(String client, ConfigurationSection downloaderSection) {
        if (downloaderSection.getString("name") != null) {
            downloaderSection.set("name", downloaderSection.getString("name", "").replace(".", "-"));
        }
        Downloader downloader = null;
        switch (downloaderSection.getString("type").toLowerCase(Locale.ROOT)) {
            case "qbittorrent" -> downloader = QBittorrent.loadFromConfig(client, downloaderSection, alertManager);
            case "qbittorrentee" -> downloader = QBittorrentEE.loadFromConfig(client, downloaderSection, alertManager);
            case "transmission" ->
                    downloader = Transmission.loadFromConfig(client, pbhServerAddress, downloaderSection, alertManager);
            case "biglybt" -> downloader = BiglyBT.loadFromConfig(client, downloaderSection, alertManager);
            case "deluge" -> downloader = Deluge.loadFromConfig(client, downloaderSection, alertManager);
            case "bitcomet" -> downloader = BitComet.loadFromConfig(client, downloaderSection, alertManager);
            //case "rtorrent" -> downloader = RTorrent.loadFromConfig(client, downloaderSection);
        }
        return downloader;

    }

    public Downloader createDownloader(String client, JsonObject downloaderSection) {
        if (downloaderSection.get("name") != null) {
            downloaderSection.addProperty("name", downloaderSection.get("name").getAsString().replace(".", "-"));
        }
        Downloader downloader = null;
        switch (downloaderSection.get("type").getAsString().toLowerCase(Locale.ROOT)) {
            case "qbittorrent" -> downloader = QBittorrent.loadFromConfig(client, downloaderSection, alertManager);
            case "qbittorrentee" -> downloader = QBittorrentEE.loadFromConfig(client, downloaderSection, alertManager);
            case "transmission" ->
                    downloader = Transmission.loadFromConfig(client, pbhServerAddress, downloaderSection, alertManager);
            case "biglybt" -> downloader = BiglyBT.loadFromConfig(client, downloaderSection, alertManager);
            case "deluge" -> downloader = Deluge.loadFromConfig(client, downloaderSection, alertManager);
            case "bitcomet" -> downloader = BitComet.loadFromConfig(client, downloaderSection, alertManager);
            //case "rtorrent" -> downloader = RTorrent.loadFromConfig(client, downloaderSection);
        }
        return downloader;

    }

    public void saveDownloaders() throws IOException {
        ConfigurationSection clientSection = new MemoryConfiguration();
        for (Downloader downloader : this.downloaders) {
            clientSection.set(downloader.getName(), downloader.saveDownloader());
        }
        Main.getMainConfig().set("client", clientSection);
        Main.getMainConfig().save(Main.getMainConfigFile());
    }

    public boolean registerDownloader(Downloader downloader) {
        if (this.downloaders.stream().anyMatch(d -> d.getName().equals(downloader.getName()))) {
            return false;
        }
        this.downloaders.add(downloader);
        return true;
    }

    public void unregisterDownloader(Downloader downloader) {
        this.downloaders.remove(downloader);
    }

    private void setupIPDB() {
        try {
            String accountId = Main.getMainConfig().getString("ip-database.account-id", "");
            String licenseKey = Main.getMainConfig().getString("ip-database.license-key", "");
            String databaseCity = Main.getMainConfig().getString("ip-database.database-city", "");
            String databaseASN = Main.getMainConfig().getString("ip-database.database-asn", "");
            boolean autoUpdate = Main.getMainConfig().getBoolean("ip-database.auto-update");
            this.ipdb = new IPDB(new File(Main.getDataDirectory(), "ipdb"), accountId, licenseKey,
                    databaseCity, databaseASN, autoUpdate, Main.getUserAgent());
        } catch (Exception e) {
            log.info(tlUI(Lang.IPDB_INVALID, e));
        }
    }

    private void resetKnownDownloaders() {
        try {
            for (Downloader downloader : getDownloaders()) {
                var result = downloader.login();
                if (result.success()) {
                    downloader.setBanList(Collections.emptyList(), null, null, true);
                }
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.RESET_DOWNLOADER_FAILED), e);
        }
    }


    private void registerBanListInvokers() {
        banListInvoker.clear();
        banListInvoker.add(new IPFilterInvoker(this));
        banListInvoker.add(new CommandExec(this));
    }

    public void shutdown() {
        // place some clean code here
        saveBanList();
        log.info(tlUI(Lang.SHUTDOWN_CLOSE_METRICS));
        this.metrics.close();
        log.info(tlUI(Lang.SHUTDOWN_UNREGISTER_MODULES));
        this.moduleManager.unregisterAll();
        log.info(tlUI(Lang.SHUTDOWN_CLOSE_DATABASE));
        this.databaseManager.close();
        log.info(tlUI(Lang.SHUTDOWN_CLEANUP_RESOURCES));
        this.moduleMatchCache.close();
        if (this.ipdb != null) {
            this.ipdb.close();
        }
        for (Downloader d : this.downloaders) {
            try {
                d.close();
            } catch (Exception e) {
                log.error(tlUI(Lang.UNABLE_CLOSE_DOWNLOADER, d.getName()), e);
            }
        }
        log.info(tlUI(Lang.SHUTDOWN_DONE));
        Main.getReloadManager().unregister(this);
    }

    private void loadBanListToMemory() {
        if (!Main.getMainConfig().getBoolean("persist.banlist")) {
            return;
        }
        this.BAN_LIST.clear();
        try {
            Map<PeerAddress, BanMetadata> data = banListDao.readBanList();
            this.BAN_LIST.putAll(data);
            log.info(tlUI(Lang.LOAD_BANLIST_FROM_FILE, data.size()));
            getDownloaders().forEach(downloader -> {
                if (downloader.login().success()) {
                    downloader.setBanList(BAN_LIST.values(), null, null, true);
                }
            });
            Collection<TorrentWrapper> relaunch = data.values().stream().map(BanMetadata::getTorrent).toList();
            getDownloaders().forEach(downloader -> downloader.relaunchTorrentIfNeededByTorrentWrapper(relaunch));
        } catch (Exception e) {
            log.error(tlUI(Lang.ERR_UPDATE_BAN_LIST), e);
        }
    }

    private void saveBanList() {
        if (!Main.getMainConfig().getBoolean("persist.banlist")) {
            return;
        }
        try {
            int count = banListDao.saveBanList(BAN_LIST);
            //share.publishUpdate();
            log.info(tlUI(Lang.SAVED_BANLIST, count));
        } catch (Exception e) {
            log.error(tlUI(Lang.SAVE_BANLIST_FAILED), e);
        }
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

    private void registerTimer() {
        if (this.banWaveWatchDog != null) {
            this.banWaveWatchDog.close();
        }
        this.banWaveWatchDog = new WatchDog("BanWave Thread", Main.getProfileConfig().getLong("check-interval", 5000) + (1000 * 60), this::watchDogHungry, null);
        registerBanWaveTimer();
        this.banWaveWatchDog.start();
    }

    private void registerBanWaveTimer() {
        if (BAN_WAVE_SERVICE != null && (!BAN_WAVE_SERVICE.isShutdown() || !BAN_WAVE_SERVICE.isTerminated())) {
            BAN_WAVE_SERVICE.shutdownNow();
        }
        BAN_WAVE_SERVICE = Executors.newScheduledThreadPool(1, r -> {
            Thread thread = new Thread(r);
            thread.setName("Ban Wave");
            thread.setDaemon(true);
            return thread;
        });
        BAN_WAVE_SERVICE.scheduleWithFixedDelay(this::banWave, 1, Main.getProfileConfig().getLong("check-interval", 5000), TimeUnit.MILLISECONDS);
    }


    private void watchDogHungry() {
        StringBuilder threadDump = new StringBuilder(System.lineSeparator());
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(true, true)) {
            threadDump.append(MsgUtil.threadInfoToString(threadInfo));
        }
        log.info(threadDump.toString());
        registerBanWaveTimer();
        Main.getGuiManager().createNotification(Level.WARNING, tlUI(Lang.BAN_WAVE_WATCH_DOG_TITLE), tlUI(Lang.BAN_WAVE_WATCH_DOG_DESCRIPTION));
    }


    /**
     * 启动新的一轮封禁序列
     */
    public void banWave() {
        try {
            if (!banWaveLock.tryLock(3, TimeUnit.SECONDS)) {
                return;
            }
            if (isGlobalPaused()) {
                if (needReApplyBanList.get()) {
                    getDownloaders().forEach(downloader -> {
                        if (downloader.login().success()) {
                            downloader.setBanList(BAN_LIST.values(), null, null, true);
                        }
                    });
                }
                return;
            }
            banWaveWatchDog.setLastOperation("Ban wave - start");
            long startTimer = System.currentTimeMillis();
            // 重置所有下载器状态为健康，这样后面失败就会对其降级
            banWaveWatchDog.setLastOperation("Reset last status");
            // 声明基本集合
            // 需要重启的种子列表
            Map<Downloader, Collection<Torrent>> needRelaunched = new ConcurrentHashMap<>();
            // 执行计划任务
            banWaveWatchDog.setLastOperation("Run scheduled tasks");
            getDownloaders().forEach(Downloader::runScheduleTasks);
            // 被解除封禁的对等体列表
            banWaveWatchDog.setLastOperation("Remove expired bans");
            Collection<BanMetadata> unbannedPeers = removeExpiredBans();
            // 被新封禁的对等体列表
            Collection<BanMetadata> bannedPeers = new CopyOnWriteArrayList<>();
            // 当前所有活跃的对等体列表
            banWaveWatchDog.setLastOperation("Collect peers");
            Map<Downloader, Map<Torrent, List<Peer>>> peers = collectPeers();
            // 更新 LIVE_PEERS 用于数据展示
            banWaveWatchDog.setLastOperation("Update live peers");
            executor.submit(() -> updateLivePeers(peers));
            // ========== 处理封禁逻辑 ==========
            Map<Downloader, List<BanDetail>> downloaderBanDetailMap = new ConcurrentHashMap<>();
            banWaveWatchDog.setLastOperation("Check Bans");
            try (TimeoutProtect protect = new TimeoutProtect(ExceptedTime.CHECK_BANS.getTimeout(), (t) -> {
                log.error(tlUI(Lang.TIMING_CHECK_BANS));
            })) {
                peers.keySet().forEach(downloader -> protect.getService().submit(() -> {
                    try {
                        downloaderBanDetailMap.put(downloader, checkBans(peers.get(downloader), downloader));
                    } catch (Exception e) {
                        log.error("Unexpected fatal error occurred while checking bans!", e);
                        throw e;
                    }
                }));
            }


            // 处理计划操作
            int scheduled = 0;
            while (!scheduledBanListOperations.isEmpty()) {
                ScheduledBanListOperation ops = scheduledBanListOperations.poll();
                scheduled++;
                if (ops.ban()) {
                    ScheduledPeerBanning banning = (ScheduledPeerBanning) ops.object();
                    List<BanDetail> banDetails = downloaderBanDetailMap.getOrDefault(banning.downloader(), new CopyOnWriteArrayList<>());
                    banDetails.add(banning.detail());
                    downloaderBanDetailMap.put(banning.downloader(), banDetails);
                } else {
                    PeerAddress address = (PeerAddress) ops.object();
                    BanMetadata banMetadata = BAN_LIST.get(address);
                    if (banMetadata != null) {
                        unbannedPeers.add(banMetadata);
                    }
                }
            }
            if (scheduled > 0) {
                log.info(tlUI(Lang.SCHEDULED_OPERATIONS, scheduled));
            }
            // 添加被封禁的 Peers 到封禁列表中
            banWaveWatchDog.setLastOperation("Add banned peers into banlist");
            try (TimeoutProtect protect = new TimeoutProtect(ExceptedTime.ADD_BAN_ENTRY.getTimeout(), (t) -> {
                log.error(tlUI(Lang.TIMING_ADD_BANS));
            })) {
                Callable<Object> callable = () -> {
                    var banlistClone = List.copyOf(BAN_LIST.keySet());
                    downloaderBanDetailMap.forEach((downloader, details) -> {
                        try {
                            List<Torrent> relaunch = Collections.synchronizedList(new ArrayList<>());
                            details.forEach(detail -> protect.getService().submit(() -> {
                                try {
                                    var action = detail.result().action();
                                    if (!downloader.getFeatureFlags().contains(DownloaderFeatureFlag.THROTTLING)) {
                                        if (action == PeerAction.THROTTLE) {
                                            action = PeerAction.BAN; // 如果下载器不支持节流，转换为封禁
                                        }
                                    }
                                    // 公共部分，开始
                                    long actualBanDuration = banDuration;
                                    if (detail.banDuration() > 0) {
                                        actualBanDuration = detail.banDuration();
                                    }
                                    BanMetadata banMetadata = new BanMetadata(
                                            detail.result().moduleContext().getName(),
                                            downloader.getName(),
                                            System.currentTimeMillis(),
                                            System.currentTimeMillis() + actualBanDuration,
                                            BanBehavior.fromPeerAction(action),
                                            detail.result().throttledUploadRate(),
                                            detail.result().throttledDownloadRate(),
                                            detail.torrent(), detail.peer(), detail.result().rule(), detail.result().reason());
                                    bannedPeers.add(banMetadata);
                                    // 公共部分，结束
                                    if (banMetadata.getBanBehavior() == BanBehavior.BAN) {
                                        relaunch.add(detail.torrent());
                                        banPeer(banlistClone, banMetadata, detail.torrent(), detail.peer());
                                        if (banMetadata.getBanBehavior() == BanBehavior.BAN) {
                                            log.info(tlUI(Lang.BAN_PEER,
                                                    detail.peer().getPeerAddress(),
                                                    detail.peer().getPeerId(),
                                                    detail.peer().getClientName(),
                                                    detail.peer().getProgress(),
                                                    detail.peer().getUploaded(),
                                                    detail.peer().getDownloaded(),
                                                    detail.torrent().getName(),
                                                    tlUI(detail.result().reason())));
                                        }
                                    }
                                    if (banMetadata.getBanBehavior() == BanBehavior.THROTTLE) {
                                        banPeer(banlistClone, banMetadata, detail.torrent(), detail.peer());
                                        log.info(tlUI(Lang.THROTTLE_PEER,
                                                detail.peer().getPeerAddress(),
                                                detail.peer().getPeerId(),
                                                detail.peer().getClientName(),
                                                detail.peer().getProgress(),
                                                detail.peer().getUploaded(),
                                                detail.peer().getDownloaded(),
                                                detail.torrent().getName(),
                                                tlUI(detail.result().reason()),
                                                detail.result().throttledUploadRate(),
                                                detail.result().throttledDownloadRate()));
                                    }
                                } catch (Exception e) {
                                    log.error(tlUI(Lang.BAN_PEER_EXCEPTION), e);
                                }
                            }));
                            needRelaunched.put(downloader, relaunch);
                        } catch (Exception e) {
                            log.error(tlUI(Lang.UNABLE_COMPLETE_PEER_BAN_TASK), e);
                        }
                    });
                    return null;
                };
                if (laboratory.isExperimentActivated(Experiments.TRANSACTION_BATCH_BAN_HISTORY_WRITE.getExperiment())) {
                    synchronized (AbstractPBHDao.class) {
                        TransactionManager.callInTransaction(databaseManager.getDataSource(), callable);
                    }
                } else {
                    callable.call();
                }
            }
            banWaveWatchDog.setLastOperation("Apply banlist");
            // 如果需要，则应用更改封禁列表到下载器
            try (TimeoutProtect protect = new TimeoutProtect(ExceptedTime.APPLY_BANLIST.getTimeout(), (t) -> {
                log.error(tlUI(Lang.TIMING_APPLY_BAN_LIST));
            })) {
                if (!needReApplyBanList.get()) {
                    getDownloaders().forEach(downloader -> protect.getService().submit(() ->
                            updateDownloader(downloader, !bannedPeers.isEmpty() || !unbannedPeers.isEmpty(),
                                    needRelaunched.getOrDefault(downloader, Collections.emptyList()),
                                    bannedPeers, unbannedPeers, false)));
                } else {
                    log.info(tlUI(Lang.APPLYING_FULL_BANLIST_TO_DOWNLOADER));
                    getDownloaders().forEach(downloader -> protect.getService().submit(() -> {
                        List<Torrent> torrents = downloader.getTorrents();
                        var list = BAN_LIST.values().stream().map(meta -> meta.getTorrent().getId()).toList();
                        torrents.removeIf(torrent -> !list.contains(torrent.getId()));
                        updateDownloader(downloader, true,
                                torrents, null, null, true);
                    }));
                    needReApplyBanList.set(false);
                }
            }
            if (!hideFinishLogs && !getDownloaders().isEmpty()) {
                long downloadersCount = peers.keySet().size();
                long torrentsCount = peers.values().stream().mapToLong(e -> e.keySet().size()).sum();
                long peersCount = peers.values().stream().flatMap(e -> e.values().stream()).mapToLong(List::size).sum();
                log.info(tlUI(Lang.BAN_WAVE_CHECK_COMPLETED, downloadersCount, torrentsCount, peersCount, bannedPeers.size(), unbannedPeers.size(), System.currentTimeMillis() - startTimer));
            }
            banWaveWatchDog.setLastOperation("Completed");
        } catch (InterruptedException e) {
            log.error("Thread interrupted");
            Thread.currentThread().interrupt();
        } catch (Throwable throwable) {
            log.error(tlUI(Lang.UNABLE_COMPLETE_SCHEDULE_TASKS), throwable);
        } finally {
            banWaveWatchDog.feed();
            metrics.recordCheck();
            banWaveLock.unlock();
        }
    }

    private List<BanDetail> checkBans(Map<Torrent, List<Peer>> provided, @NotNull Downloader downloader) {
        List<BanDetail> details = Collections.synchronizedList(new ArrayList<>());
        try (TimeoutProtect protect = new TimeoutProtect(ExceptedTime.CHECK_BANS.getTimeout(), (t) -> log.error(tlUI(Lang.TIMING_CHECK_BANS)))) {
            for (Torrent torrent : provided.keySet()) {
                List<Peer> peers = provided.get(torrent);
                for (Peer peer : peers) {
                    protect.getService().submit(() -> {
                        try {
                            CheckResult checkResult = checkBan(torrent, peer, downloader);
                            details.add(new BanDetail(torrent, peer, checkResult, checkResult.duration()));
                        } catch (Exception e) {
                            log.error("Unexpected error occurred while checking bans", e);
                            throw e;
                        }
                    });
                }
            }
        }
        return details;
    }

    private void updateLivePeers(Map<Downloader, Map<Torrent, List<Peer>>> peers) {
        Map<PeerAddress, List<PeerMetadata>> livePeers = new HashMap<>(128);
        peers.forEach((downloader, tasks) ->
                tasks.forEach((torrent, peer) ->
                        peer.forEach(p -> {
                                    PeerAddress address = p.getPeerAddress();
                                    List<PeerMetadata> data = livePeers.getOrDefault(address, new ArrayList<>());
                                    PeerMetadata metadata = new PeerMetadata(
                                            downloader.getName(),
                                            torrent, p);
                                    data.add(metadata);
                                    livePeers.put(address, data);
                                }
                        )));
        LIVE_PEERS = Map.copyOf(livePeers);
        Main.getEventBus().post(new LivePeersUpdatedEvent(LIVE_PEERS));
    }

    /**
     * 如果需要，则更新下载器的封禁列表
     * 对于 Transmission 等下载器来说，传递 needToRelaunch 会重启对应 Torrent
     *
     * @param downloader     要操作的下载器
     * @param updateBanList  是否需要从 BAN_LIST 常量更新封禁列表到下载器
     * @param needToRelaunch 传递一个集合，包含需要重启的种子；并非每个下载器都遵守此行为；对于 qbittorrent 等 banlist 可被实时应用的下载器来说，不会重启 Torrent
     */
    public void updateDownloader(@NotNull Downloader downloader, boolean updateBanList, @NotNull Collection<Torrent> needToRelaunch, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList) {
        if (!updateBanList && needToRelaunch.isEmpty()) return;
        try {
            var loginResult = downloader.login();
            if (!loginResult.success()) {
                if (loginResult.getStatus() != DownloaderLoginResult.Status.PAUSED) {
                    log.error(tlUI(Lang.ERR_CLIENT_LOGIN_FAILURE_SKIP, downloader.getName(), downloader.getEndpoint(), tlUI(loginResult.getMessage())));
                    downloader.setLastStatus(DownloaderLastStatus.ERROR, loginResult.getMessage());
                }
                return;
            } else {
                downloader.setLastStatus(DownloaderLastStatus.HEALTHY, loginResult.getMessage());
            }
            downloader.setBanList(BAN_LIST.values(), added, removed, applyFullList);
            downloader.relaunchTorrentIfNeeded(needToRelaunch);
        } catch (Throwable th) {
            log.error(tlUI(Lang.ERR_UPDATE_BAN_LIST, downloader.getName(), downloader.getEndpoint()), th);
            downloader.setLastStatus(DownloaderLastStatus.ERROR, new TranslationComponent(Lang.STATUS_TEXT_EXCEPTION, th.getClass().getName() + ": " + th.getMessage()));
        }
    }

    /**
     * 移除过期的封禁
     *
     * @return 当封禁条目过期时，移除它们（解封禁）
     */
    public Collection<BanMetadata> removeExpiredBans() {
        List<PeerAddress> removeBan = new ArrayList<>();
        List<BanMetadata> metadata = new ArrayList<>();
        for (Map.Entry<PeerAddress, BanMetadata> pair : BAN_LIST.entrySet()) {
            if (System.currentTimeMillis() >= pair.getValue().getUnbanAt()) {
                removeBan.add(pair.getKey());
                metadata.add(pair.getValue());
            }
        }
        removeBan.forEach(this::unbanPeer);
        long normalUnbanCount = metadata.stream().filter(meta -> meta.getBanBehavior() != BanBehavior.DISCONNECT).count();
        if (normalUnbanCount > 0) {
            log.info(tlUI(Lang.PEER_UNBAN_WAVE, normalUnbanCount));
        }
        return metadata;
    }

    /**
     * 注册 Modules
     */
    private void registerModules() {
        log.info(tlUI(Lang.WAIT_FOR_MODULES_STARTUP));
        moduleManager.register(IPBlackList.class);
        moduleManager.register(PeerIdBlacklist.class);
        moduleManager.register(ClientNameBlacklist.class);
        moduleManager.register(ExpressionRule.class);
        moduleManager.register(ProgressCheatBlocker.class);
        moduleManager.register(MultiDialingBlocker.class);
        //moduleManager.register(new ActiveProbing(this, profile));
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
        //moduleManager.register(PBHLogsController.class);
        moduleManager.register(ActiveMonitoringModule.class);
        moduleManager.register(PBHPlusController.class);
        moduleManager.register(PBHOOBEController.class);
        moduleManager.register(PBHChartController.class);
        moduleManager.register(PBHGeneralController.class);
        moduleManager.register(PBHTorrentController.class);
        moduleManager.register(PBHPeerController.class);
        moduleManager.register(PBHAlertController.class);
        moduleManager.register(PBHLogsController.class);
        moduleManager.register(PBHPushController.class);
        moduleManager.register(PBHLabController.class);
        moduleManager.register(PBHEasterEggController.class);
        moduleManager.register(PBHUtilitiesController.class);
        moduleManager.register(BackgroundModule.class);
    }

    public Map<Downloader, Map<Torrent, List<Peer>>> collectPeers() {
        Map<Downloader, Map<Torrent, List<Peer>>> peers = new HashMap<>();
        try (var service = Executors.newVirtualThreadPerTaskExecutor()) {
            getDownloaders().forEach(downloader -> service.submit(() -> {
                try {
                    Map<Torrent, List<Peer>> p = collectPeers(downloader);
                    if (p != null) {
                        peers.put(downloader, p);
                    }
                } catch (Exception e) {
                    log.error(tlUI(Lang.DOWNLOADER_UNHANDLED_EXCEPTION), e);
                }
            }));
        }
        return peers;
    }

    @Nullable
    public Map<Torrent, List<Peer>> collectPeers(Downloader downloader) {
        Map<Torrent, List<Peer>> peers = new ConcurrentHashMap<>();
        var loginResult = downloader.login();
        if (!loginResult.success()) {
            if (loginResult.getStatus() != DownloaderLoginResult.Status.PAUSED) {
                log.error(tlUI(Lang.ERR_CLIENT_LOGIN_FAILURE_SKIP, downloader.getName(), downloader.getEndpoint(), tlUI(loginResult.getMessage())));
                downloader.setLastStatus(DownloaderLastStatus.ERROR, loginResult.getMessage());
                if (loginResult.getStatus() == DownloaderLoginResult.Status.MISSING_COMPONENTS || loginResult.getStatus() == DownloaderLoginResult.Status.REQUIRE_TAKE_ACTIONS) {
                    downloader.setLastStatus(DownloaderLastStatus.NEED_TAKE_ACTION, loginResult.getMessage());
                }
            }
            return null;
        }
        List<Torrent> torrents = downloader.getTorrents();
        Semaphore parallelReqRestrict = new Semaphore(downloader.getMaxConcurrentPeerRequestSlots());
        try (TimeoutProtect protect = new TimeoutProtect(ExceptedTime.COLLECT_PEERS.getTimeout(), (t) -> {
            log.error(tlUI(Lang.TIMING_COLLECT_PEERS));
        })) {
            torrents.forEach(torrent -> protect.getService().submit(() -> {
                try {
                    parallelReqRestrict.acquire();
                    var p = downloader.getPeers(torrent);
                    peers.put(torrent, p);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error(tlUI(Lang.UNABLE_RETRIEVE_PEERS), e);
                } finally {
                    parallelReqRestrict.release();
                }
            }));
            downloader.setLastStatus(DownloaderLastStatus.HEALTHY, new TranslationComponent(Lang.STATUS_TEXT_OK));
        }

        return peers;
    }

    public IPDBResponse queryIPDB(PeerAddress address) {
        try {
            return geoIpCache.get(address.getIp(), () -> {
                if (ipdb == null) {
                    return new IPDBResponse(new LazyLoad<>(() -> null));
                } else {
                    return new IPDBResponse(new LazyLoad<>(() -> {
                        try {
                            return ipdb.query(address.getAddress().toInetAddress());
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


    /**
     * 检查一个在给定 Torrent 上的对等体是否需要被封禁
     *
     * @param torrent Torrent
     * @param peer    对等体
     * @return 封禁规则检查结果
     */
    @NotNull
    public CheckResult checkBan(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader) {
        List<CheckResult> results = new ArrayList<>();
        var node = ignoreAddresses.elementsContaining(peer.getPeerAddress().getAddress());
        if (node != null) {
            // 检查 Peer 的 Flags，如果不支持 Flags 或者 Flags 同时满足这些条件：
            // 来自 DHT、PEX、Tracker 的其中一个
            // 是入站连接
            // 则认为用户搞砸了 NAT 设置，发出重要提醒
            if (peer.getFlags() == null
                    || peer.getFlags().isFromIncoming()
                    || !peer.getFlags().isOutgoingConnection()
                    || peer.getFlags().isFromTracker()
                    || peer.getFlags().isFromDHT()
                    || peer.getFlags().isFromPEX()) {
                if (!peer.isHandshaking()) {
                    var addr = peer.getPeerAddress().getAddress();
                    if (addr.isIPv4Convertible()) {
                        addr = addr.toIPv4();
                    }
                    var addrStr = addr.toNormalizedString();
                    if ((addrStr.endsWith(".1") || addrStr.endsWith(".0") || addrStr.endsWith(".254") || addrStr.endsWith(".255")) && (addr.isLocal() || addr.isAnyLocal())) {
                        if (!alertManager.identifierAlertExistsIncludeRead("downloader-nat-setup-error@" + downloader.getName())) {
                            alertManager.publishAlert(true, AlertLevel.ERROR, "downloader-nat-setup-error@" + downloader.getName(),
                                    new TranslationComponent(Lang.DOWNLOADER_DOCKER_INCORRECT_NETWORK_DETECTED_TITLE),
                                    new TranslationComponent(Lang.DOWNLOADER_DOCKER_INCORRECT_NETWORK_DETECTED_DESCRIPTION, downloader.getName(), peer.getPeerAddress().getAddress().toNormalizedString()));
                        }
                    }
                }
            }
            return new CheckResult(getClass(), PeerAction.SKIP, 0, -1L, -1L,
                    new TranslationComponent("general-rule-ignored-address"),
                    new TranslationComponent("general-reason-skip-ignored-peers"));
        }
        try {
            for (FeatureModule registeredModule : moduleManager.getModules()) {
                if (!(registeredModule instanceof RuleFeatureModule module)) {
                    continue;
                }
                try {
                    CheckResult checkResult;
                    if (module.isThreadSafe()) {
                        checkResult = module.shouldBanPeer(torrent, peer, downloader, executor);
                    } else {
                        registeredModule.getThreadLock().lock();
                        try {
                            checkResult = module.shouldBanPeer(torrent, peer, downloader, executor);
                        } finally {
                            registeredModule.getThreadLock().unlock();
                        }
                    }
                    if (checkResult.action() == PeerAction.SKIP) {
                        results.add(checkResult);
                    }
                    results.add(checkResult);
                } catch (Exception e) {
                    log.error(tlUI(Lang.UNABLE_EXECUTE_MODULE, module.getName()), e);
                }
            }
            CheckResult result = NO_MATCHES_CHECK_RESULT;
            for (CheckResult r : results) {
                if (r.action() == PeerAction.SKIP) {
                    result = r;
                    break; // 立刻离开循环，处理跳过
                }
                if (r.action() == PeerAction.THROTTLE) {
                    result = r;
                }
                if (r.action() == PeerAction.BAN || r.action() == PeerAction.BAN_FOR_DISCONNECT) {
                    result = r;
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to execute modules", e);
            return new CheckResult(getClass(), PeerAction.NO_ACTION, 0, -1L, -1L,
                    new TranslationComponent("ERROR"), new TranslationComponent("ERROR"));
        }
    }

    /**
     * 获取目前所有被封禁的对等体的集合的拷贝
     *
     * @return 不可修改的集合拷贝
     */
    @NotNull
    public Map<PeerAddress, BanMetadata> getBannedPeers() {
        return Map.copyOf(BAN_LIST);
    }

    /**
     * 获取目前所有被封禁的对等体的集合的拷贝
     *
     * @return 不可修改的集合拷贝
     */
    @NotNull
    public Map<PeerAddress, BanMetadata> getBannedPeersDirect() {
        return BAN_LIST;
    }

    /**
     * 以指定元数据封禁一个特定的对等体
     *
     * @param compareWith 对比 BanList，默认 BAN_LIST 或者 BAN_LIST 的克隆
     * @param peer        对等体 IP 地址
     * @param banMetadata 封禁元数据
     */
    private void banPeer(@NotNull Collection<PeerAddress> compareWith, @NotNull BanMetadata banMetadata, @NotNull Torrent torrentObj, @NotNull Peer peer) {
        if (compareWith.contains(peer.getPeerAddress())) {
            log.error(tlUI(Lang.DUPLICATE_BAN, banMetadata));
            needReApplyBanList.set(true);
            log.warn(tlUI(Lang.SCHEDULED_FULL_BANLIST_APPLY));
        }
        BAN_LIST.put(peer.getPeerAddress(), banMetadata);
        metrics.recordPeerBan(peer.getPeerAddress(), banMetadata);
        banListInvoker.forEach(i -> i.add(peer.getPeerAddress(), banMetadata));
        banMetadata.setReverseLookup("N/A");
        if (Main.getMainConfig().getBoolean("lookup.dns-reverse-lookup")) {
            executor.submit(() -> {
                if (laboratory.isExperimentActivated(Experiments.DNSJAVA.getExperiment())) {
                    dnsLookup.ptr(peer.getPeerAddress().getAddress().toReverseDNSLookupString()).thenAccept(hostName -> {
                        if (hostName.isPresent()) {
                            if (!peer.getPeerAddress().getIp().equals(hostName.get())) {
                                banMetadata.setReverseLookup(hostName.get());
                            }
                        }
                    });
                } else {
                    String hostName = peer.getPeerAddress().getAddress().toInetAddress().getHostName();
                    if (!peer.getPeerAddress().getIp().equals(hostName)) {
                        banMetadata.setReverseLookup(peer.getPeerAddress().getAddress().toInetAddress().getHostName());
                    }
                }
            });
        }
        Main.getEventBus().post(new PeerBanEvent(peer.getPeerAddress(), banMetadata, torrentObj, peer));
    }

    public void scheduleBanPeer(@NotNull BanMetadata banMetadata, @NotNull Torrent torrent, @NotNull Peer peer) {
        Downloader downloader = getDownloaders().stream().filter(d -> d.getName().equals(banMetadata.getDownloader()))
                .findFirst().orElseThrow();
        banPeer(BAN_LIST.keySet(), banMetadata, torrent, peer);
        scheduledBanListOperations.add(new ScheduledBanListOperation(true, new ScheduledPeerBanning(
                downloader,
                new BanDetail(torrent,
                        peer,
                        new CheckResult(getClass(), PeerAction.BAN, banDuration,
                                -1L, -1L,
                                new TranslationComponent(Lang.USER_MANUALLY_BAN_RULE),
                                new TranslationComponent(Lang.USER_MANUALLY_BAN_REASON))
                        , banDuration)
        )));
    }

    public void scheduleUnBanPeer(@NotNull PeerAddress peer) {
        unbanPeer(peer);
        scheduledBanListOperations.add(new ScheduledBanListOperation(false, peer));
    }

    public String getWebUiUrl() {
        return "http://localhost:" + Main.getServer().getHttpdPort() + "/?token=" + UrlEncoderDecoder.encodePath(webContainer.getToken());
    }

    public List<Downloader> getDownloaders() {
        return List.copyOf(downloaders);
    }

    /**
     * 解除一个指定对等体
     *
     * @param address 对等体 IP 地址
     * @return 此对等体的封禁元数据；返回 null 代表此对等体没有被封禁
     */
    @Nullable
    private BanMetadata unbanPeer(@NotNull PeerAddress address) {
        BanMetadata metadata = BAN_LIST.remove(address);
        if (metadata != null) {
            metrics.recordPeerUnban(address, metadata);
            banListInvoker.forEach(i -> i.remove(address, metadata));
        }
        Main.getEventBus().post(new PeerUnbanEvent(address, metadata));
        return metadata;
    }

    public Map<PeerAddress, List<PeerMetadata>> getLivePeersSnapshot() {
        return LIVE_PEERS;
    }

    public void setGlobalPaused(boolean globalPaused) {
        this.globalPaused = globalPaused;
        if (globalPaused) {
            ExchangeMap.GUI_DISPLAY_FLAGS.add(new ExchangeMap.DisplayFlag("global-paused", 20, tlUI(Lang.STATUS_BAR_GLOBAL_PAUSED)));
        } else {
            ExchangeMap.GUI_DISPLAY_FLAGS.removeIf(f -> "global-paused".equals(f.getId()));
        }
    }

    /**
     * Use @Autowired if available
     *
     * @return JavalinWebContainer
     */
    @Nullable
    @Deprecated
    public JavalinWebContainer getWebContainer() {
        return webContainer;
    }

    public record IPDBResponse(
            LazyLoad<IPGeoData> geoData
    ) {
    }

    public record BanDetail(
            Torrent torrent,
            Peer peer,
            CheckResult result,
            long banDuration
    ) {
    }

    public record ScheduledPeerBanning(
            Downloader downloader,
            BanDetail detail
    ) {
    }

    private record ScheduledBanListOperation(boolean ban, Object object) {
    }
}
