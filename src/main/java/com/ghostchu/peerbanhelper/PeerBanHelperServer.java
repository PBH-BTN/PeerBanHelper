package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.DatabaseHelper;
import com.ghostchu.peerbanhelper.database.dao.impl.BanListDao;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.downloader.impl.biglybt.BiglyBT;
import com.ghostchu.peerbanhelper.downloader.impl.deluge.Deluge;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.QBittorrent;
import com.ghostchu.peerbanhelper.downloader.impl.rtorrent.RTorrent;
import com.ghostchu.peerbanhelper.downloader.impl.transmission.Transmission;
import com.ghostchu.peerbanhelper.event.LivePeersUpdatedEvent;
import com.ghostchu.peerbanhelper.event.PBHServerStartedEvent;
import com.ghostchu.peerbanhelper.event.PeerBanEvent;
import com.ghostchu.peerbanhelper.event.PeerUnbanEvent;
import com.ghostchu.peerbanhelper.invoker.BanListInvoker;
import com.ghostchu.peerbanhelper.invoker.impl.CommandExec;
import com.ghostchu.peerbanhelper.invoker.impl.IPFilterInvoker;
import com.ghostchu.peerbanhelper.ipdb.IPDB;
import com.ghostchu.peerbanhelper.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.metric.HitRateMetric;
import com.ghostchu.peerbanhelper.module.*;
import com.ghostchu.peerbanhelper.module.impl.rule.*;
import com.ghostchu.peerbanhelper.module.impl.webapi.*;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.*;
import com.ghostchu.peerbanhelper.util.rule.ModuleMatchCache;
import com.ghostchu.peerbanhelper.util.time.ExceptedTime;
import com.ghostchu.peerbanhelper.util.time.TimeoutProtect;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.wrapper.PeerMetadata;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonObject;
import inet.ipaddr.IPAddress;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.configuration.MemoryConfiguration;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

@Slf4j
@Component
public class PeerBanHelperServer {
    private static final long BANLIST_SAVE_INTERVAL = 60 * 60 * 1000;
    private final CheckResult NO_MATCHES_CHECK_RESULT = new CheckResult(getClass(), PeerAction.NO_ACTION, 0, "No matches", "No matches");
    private final Map<PeerAddress, BanMetadata> BAN_LIST = new ConcurrentHashMap<>();
    private final List<Downloader> downloaders = new ArrayList<>();
    @Getter
    private final List<IPAddress> ignoreAddresses = new ArrayList<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    @Getter
    private final List<BanListInvoker> banListInvoker = new ArrayList<>();
    private final String pbhServerAddress;
    private final Set<ScheduledPeerBanning> scheduledPeerBannings = new CopyOnWriteArraySet<>();
    private final Set<PeerAddress> scheduledPeerUnBannings = new CopyOnWriteArraySet<>();
    @Getter
    private YamlConfiguration profileConfig;
    @Getter
    private long banDuration;
    @Getter
    private int httpdPort;
    @Getter
    private boolean hideFinishLogs;
    @Getter
    private YamlConfiguration mainConfig;
    @Autowired
    private ModuleMatchCache moduleMatchCache;
    @Autowired
    @Qualifier("banListFile")
    private File banListFile;
    private ScheduledExecutorService BAN_WAVE_SERVICE;
    private ScheduledExecutorService GENERAL_SCHEDULER = Executors.newScheduledThreadPool(8, Thread.ofVirtual().factory());
    @Getter
    private Map<PeerAddress, PeerMetadata> LIVE_PEERS = new HashMap<>();
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
    private Cache<String, IPDBResponse> geoIpCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(3000)
            .softValues()
            .build();
    @Getter
    private HitRateMetric hitRateMetric = new HitRateMetric();
    @Autowired
    private DatabaseHelper databaseHelper;
    @Autowired
    private BanListDao banListDao;

    public PeerBanHelperServer() {
        this.pbhServerAddress = Main.getPbhServerAddress();
        this.profileConfig = Main.getProfileConfig();
        this.banDuration = profileConfig.getLong("ban-duration");
        this.mainConfig = Main.getMainConfig();
        this.httpdPort = mainConfig.getInt("server.http");
        this.hideFinishLogs = mainConfig.getBoolean("logger.hide-finish-log");
        profileConfig.getStringList("ignore-peers-from-addresses").forEach(ip -> {
            IPAddress ignored = IPAddressUtil.getIPAddress(ip);
            ignoreAddresses.add(ignored);
        });
    }

    public void start() throws SQLException {
        loadDownloaders();
        registerHttpServer();
        setupIPDB();
        registerModules();
        resetKnownDownloaders();
        registerBanListInvokers();
        loadBanListToMemory();
        registerTimer();
        banListInvoker.forEach(BanListInvoker::reset);
        GENERAL_SCHEDULER.scheduleWithFixedDelay(this::saveBanList, 10 * 1000, BANLIST_SAVE_INTERVAL, TimeUnit.MILLISECONDS);
        Main.getEventBus().post(new PBHServerStartedEvent(this));
    }

    public void loadDownloaders() {
        this.downloaders.clear();
        ConfigurationSection clientSection = mainConfig.getConfigurationSection("client");
        for (String client : clientSection.getKeys(false)) {
            ConfigurationSection downloaderSection = clientSection.getConfigurationSection(client);
            String endpoint = downloaderSection.getString("endpoint");
            Downloader downloader = createDownloader(client, downloaderSection);
            registerDownloader(downloader);
            log.info(Lang.DISCOVER_NEW_CLIENT, downloader.getType(), client, endpoint);
        }
    }

    public Downloader createDownloader(String client, ConfigurationSection downloaderSection) {
        Downloader downloader = null;
        switch (downloaderSection.getString("type").toLowerCase(Locale.ROOT)) {
            case "qbittorrent" -> downloader = QBittorrent.loadFromConfig(client, downloaderSection);
            case "transmission" ->
                    downloader = Transmission.loadFromConfig(client, pbhServerAddress, downloaderSection);
            case "biglybt" -> downloader = BiglyBT.loadFromConfig(client, downloaderSection);
            case "deluge" -> downloader = Deluge.loadFromConfig(client, downloaderSection);
            case "rtorrent" -> downloader = RTorrent.loadFromConfig(client, downloaderSection);
        }
        return downloader;

    }

    public Downloader createDownloader(String client, JsonObject downloaderSection) {
        Downloader downloader = null;
        switch (downloaderSection.get("type").getAsString().toLowerCase(Locale.ROOT)) {
            case "qbittorrent" -> downloader = QBittorrent.loadFromConfig(client, downloaderSection);
            case "transmission" ->
                    downloader = Transmission.loadFromConfig(client, pbhServerAddress, downloaderSection);
            case "biglybt" -> downloader = BiglyBT.loadFromConfig(client, downloaderSection);
            case "deluge" -> downloader = Deluge.loadFromConfig(client, downloaderSection);
            case "rtorrent" -> downloader = RTorrent.loadFromConfig(client, downloaderSection);
        }
        return downloader;

    }

    public void saveDownloaders() throws IOException {
        ConfigurationSection clientSection = new MemoryConfiguration();
        for (Downloader downloader : this.downloaders) {
            clientSection.set(downloader.getName(), downloader.saveDownloader());
        }
        mainConfig.set("client", clientSection);
        mainConfig.save(Main.getMainConfigFile());
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
            String accountId = mainConfig.getString("ip-database.account-id", "");
            String licenseKey = mainConfig.getString("ip-database.license-key", "");
            String databaseCity = mainConfig.getString("ip-database.database-city", "");
            String databaseASN = mainConfig.getString("ip-database.database-asn", "");
            boolean autoUpdate = mainConfig.getBoolean("ip-database.auto-update");
            if (accountId.isEmpty() || licenseKey.isEmpty() || databaseCity.isEmpty() || databaseASN.isEmpty()) {
                log.warn(Lang.IPDB_NEED_CONFIG);
                return;
            }
            this.ipdb = new IPDB(new File(Main.getDataDirectory(), "ipdb"), accountId, licenseKey,
                    databaseCity, databaseASN, autoUpdate, Main.getUserAgent());
        } catch (Exception e) {
            log.info(Lang.IPDB_INVALID, e);
        }
    }

    private void resetKnownDownloaders() {
        try {
            for (Downloader downloader : downloaders) {
                downloader.login();
                downloader.setBanList(Collections.emptyList(), null, null);
            }
        } catch (Exception e) {
            log.error(Lang.RESET_DOWNLOADER_FAILED, e);
        }
    }


    private void registerBanListInvokers() {
        banListInvoker.add(new IPFilterInvoker(this));
        banListInvoker.add(new CommandExec(this));
    }

    public void shutdown() {
        // place some clean code here
        saveBanList();
        log.info(Lang.SHUTDOWN_CLOSE_METRICS);
        this.metrics.close();
        log.info(Lang.SHUTDOWN_UNREGISTER_MODULES);
        this.moduleManager.unregisterAll();
        log.info(Lang.SHUTDOWN_CLOSE_DATABASE);
        this.databaseManager.close();
        log.info(Lang.SHUTDOWN_CLEANUP_RESOURCES);
        this.moduleMatchCache.close();
        if (this.ipdb != null) {
            this.ipdb.close();
        }
        this.downloaders.forEach(d -> {
            try {
                d.close();
            } catch (Exception e) {
                log.error("Failed to close download {}", d.getName(), e);
            }
        });
        log.info(Lang.SHUTDOWN_DONE);
    }

    private void loadBanListToMemory() {
        if (!mainConfig.getBoolean("persist.banlist")) {
            return;
        }
        try {
            Map<PeerAddress, BanMetadata> data = banListDao.readBanList();
            this.BAN_LIST.putAll(data);
            log.info(Lang.LOAD_BANLIST_FROM_FILE, data.size());
            downloaders.forEach(downloader -> {
                downloader.login();
                downloader.setBanList(BAN_LIST.keySet(), null, null);
            });
            Collection<TorrentWrapper> relaunch = data.values().stream().map(BanMetadata::getTorrent).toList();
            downloaders.forEach(downloader -> downloader.relaunchTorrentIfNeededByTorrentWrapper(relaunch));
        } catch (Exception e) {
            log.error(Lang.LOAD_BANLIST_FAIL, e);
        }
    }

    private void saveBanList() {
        if (!mainConfig.getBoolean("persist.banlist")) {
            return;
        }
        try {
            int count = banListDao.saveBanList(BAN_LIST);
            log.info(Lang.SAVED_BANLIST, count);
        } catch (Exception e) {
            log.error(Lang.SAVE_BANLIST_FAILED, e);
        }
    }

    private void registerHttpServer() {
        String token = System.getenv("PBH_API_TOKEN");
        if (token == null) {
            token = System.getProperty("pbh.api_token");
        }
        if (token == null) {
            token = getMainConfig().getString("server.token");
        }
        String host = getMainConfig().getString("server.address");
        if (host.equals("0.0.0.0") || host.equals("::") || host.equals("localhost")) {
            host = null;
        }
        webContainer.start(host, httpdPort, token);
    }

    private void registerTimer() {
        this.banWaveWatchDog = new WatchDog("BanWave Thread", profileConfig.getLong("check-interval", 5000) + (1000 * 60), this::watchDogHungry, null);
        registerBanWaveTimer();
        this.banWaveWatchDog.start();
    }

    private void registerBanWaveTimer() {
        if (BAN_WAVE_SERVICE != null && (!BAN_WAVE_SERVICE.isShutdown() || !BAN_WAVE_SERVICE.isTerminated())) {
            BAN_WAVE_SERVICE.shutdownNow().forEach(r -> log.error("Unfinished runnable: {}", r));
        }
        BAN_WAVE_SERVICE = Executors.newScheduledThreadPool(1, r -> {
            Thread thread = new Thread(r);
            thread.setName("Ban Wave");
            thread.setDaemon(true);
            return thread;
        });
        log.info(Lang.PBH_BAN_WAVE_STARTED);
        BAN_WAVE_SERVICE.scheduleAtFixedRate(this::banWave, 1, profileConfig.getLong("check-interval", 5000), TimeUnit.MILLISECONDS);
    }


    private void watchDogHungry() {
        StringBuilder threadDump = new StringBuilder(System.lineSeparator());
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(true, true)) {
            threadDump.append(MsgUtil.threadInfoToString(threadInfo));
        }
        log.info(threadDump.toString());
        registerBanWaveTimer();
        Main.getGuiManager().createNotification(Level.WARNING, Lang.BAN_WAVE_WATCH_DOG_TITLE, Lang.BAN_WAVE_WATCH_DOG_DESCRIPTION);
    }


    /**
     * 启动新的一轮封禁序列
     */
    public void banWave() {
        banWaveWatchDog.setLastOperation("Ban wave - start");
        long startTimer = System.currentTimeMillis();
        try {
            // 重置所有下载器状态为健康，这样后面失败就会对其降级
            banWaveWatchDog.setLastOperation("Reset last status");
            // 声明基本集合
            // 需要重启的种子列表
            Map<Downloader, Collection<Torrent>> needRelaunched = new ConcurrentHashMap<>();
            // 被解除封禁的对等体列表
            banWaveWatchDog.setLastOperation("Remove expired bans");
            Collection<BanMetadata> unbannedPeers = removeExpiredBans();
            unbannedPeers.addAll(fetchScheduledUnbans());
            // 被新封禁的对等体列表
            Collection<BanMetadata> bannedPeers = new CopyOnWriteArrayList<>(fetchScheduledBans());
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
                log.error(Lang.TIMING_CHECK_BANS);
            })) {
                downloaders.forEach(downloader -> protect.getService().submit(() -> downloaderBanDetailMap.put(downloader, checkBans(peers.get(downloader)))));
            }
            // 添加被封禁的 Peers 到封禁列表中
            banWaveWatchDog.setLastOperation("Add banned peers into banlist");
            try (TimeoutProtect protect = new TimeoutProtect(ExceptedTime.ADD_BAN_ENTRY.getTimeout(), (t) -> {
                log.error(Lang.TIMING_ADD_BANS);
            })) {
                downloaderBanDetailMap.forEach((downloader, details) -> {
                    try {
                        List<Torrent> relaunch = Collections.synchronizedList(new ArrayList<>());
                        details.forEach(detail -> {
                            protect.getService().submit(() -> {
                                if (detail.result().action() == PeerAction.BAN) {
                                    long actualBanDuration = banDuration;
                                    if (detail.banDuration() > 0) {
                                        actualBanDuration = detail.banDuration();
                                    }
                                    BanMetadata banMetadata = new BanMetadata(detail.result().moduleContext().getName(), downloader.getName(),
                                            System.currentTimeMillis(), System.currentTimeMillis() + actualBanDuration,
                                            detail.torrent(), detail.peer(), detail.result().rule(), detail.result().reason());
                                    bannedPeers.add(banMetadata);
                                    relaunch.add(detail.torrent());
                                    banPeer(banMetadata, detail.torrent(), detail.peer());
                                    log.warn(Lang.BAN_PEER, detail.peer().getPeerAddress(), detail.peer().getPeerId(), detail.peer().getClientName(), detail.peer().getProgress(), detail.peer().getUploaded(), detail.peer().getDownloaded(), detail.torrent().getName(), detail.result().reason());
                                }
                            });
                        });

                        needRelaunched.put(downloader, relaunch);
                    } catch (Exception e) {
                        log.error("Unable to complete peer ban task, report to PBH developer!!!");
                    }
                });
            }
            banWaveWatchDog.setLastOperation("Apply banlist");
            // 如果需要，则应用更改封禁列表到下载器
            try (TimeoutProtect protect = new TimeoutProtect(ExceptedTime.APPLY_BANLIST.getTimeout(), (t) -> {
                log.error(Lang.TIMING_APPLY_BAN_LIST);
            })) {
                downloaders.forEach(downloader -> protect.getService().submit(() -> updateDownloader(downloader, !bannedPeers.isEmpty() || !unbannedPeers.isEmpty(),
                        needRelaunched.getOrDefault(downloader, Collections.emptyList()),
                        bannedPeers, unbannedPeers)));
            }
            if (!hideFinishLogs) {
                long downloadersCount = peers.keySet().size();
                long torrentsCount = peers.values().stream().mapToLong(e -> e.keySet().size()).sum();
                long peersCount = peers.values().stream().flatMap(e -> e.values().stream()).mapToLong(List::size).sum();
                log.info(Lang.BAN_WAVE_CHECK_COMPLETED, downloadersCount, torrentsCount, peersCount, bannedPeers.size(), unbannedPeers.size(), System.currentTimeMillis() - startTimer);
            }
            banWaveWatchDog.setLastOperation("Completed");
        } finally {
            banWaveWatchDog.feed();
            metrics.recordCheck();
        }
    }

    private Collection<? extends BanMetadata> fetchScheduledBans() {
        return scheduledPeerBannings.stream().map(ScheduledPeerBanning::banMetadata).toList();
    }

    private Collection<? extends BanMetadata> fetchScheduledUnbans() {
        List<BanMetadata> banMetadata = new ArrayList<>();
        var it = scheduledPeerUnBannings.iterator();
        while (it.hasNext()) {
            BanMetadata meta = BAN_LIST.get(it.next());
            if (meta != null) {
                banMetadata.add(meta);
            }
            it.remove();
        }
        return banMetadata;
    }

    private List<BanDetail> checkBans(Map<Torrent, List<Peer>> provided) {
        List<BanDetail> details = Collections.synchronizedList(new ArrayList<>());
        try (TimeoutProtect protect = new TimeoutProtect(ExceptedTime.CHECK_BANS.getTimeout(), (t) -> log.error(Lang.TIMING_CHECK_BANS))) {
            for (Torrent torrent : provided.keySet()) {
                List<Peer> peers = provided.get(torrent);
                for (Peer peer : peers) {
                    protect.getService().submit(() -> {
                        CheckResult checkResult = checkBan(torrent, peer);
                        details.add(new BanDetail(torrent, peer, checkResult, checkResult.duration()));
                    });
                }
            }
        }
        return details;
    }

    private void updateLivePeers(Map<Downloader, Map<Torrent, List<Peer>>> peers) {
        Map<PeerAddress, PeerMetadata> livePeers = new HashMap<>(128);
        peers.forEach((downloader, tasks) ->
                tasks.forEach((torrent, peer) ->
                        peer.forEach(p -> {
                                    PeerAddress address = p.getPeerAddress();
                                    PeerMetadata metadata = new PeerMetadata(
                                            downloader.getName(),
                                            torrent, p);
                                    livePeers.put(address, metadata);
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
    public void updateDownloader(@NotNull Downloader downloader, boolean updateBanList, @NotNull Collection<Torrent> needToRelaunch, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed) {
        if (!updateBanList && needToRelaunch.isEmpty()) return;
        try {
            if (!downloader.login()) {
                log.error(Lang.ERR_CLIENT_LOGIN_FAILURE_SKIP, downloader.getName(), downloader.getEndpoint());
                downloader.setLastStatus(DownloaderLastStatus.ERROR, Lang.STATUS_TEXT_LOGIN_FAILED);
                return;
            } else {
                downloader.setLastStatus(DownloaderLastStatus.HEALTHY, Lang.STATUS_TEXT_OK);
            }
            downloader.setBanList(BAN_LIST.keySet(), added, removed);
            downloader.relaunchTorrentIfNeeded(needToRelaunch);
        } catch (Throwable th) {
            log.error(Lang.ERR_UPDATE_BAN_LIST, downloader.getName(), downloader.getEndpoint(), th);
            downloader.setLastStatus(DownloaderLastStatus.ERROR, Lang.STATUS_TEXT_EXCEPTION);
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
        if (!removeBan.isEmpty()) {
            log.info(Lang.PEER_UNBAN_WAVE, removeBan.size());
        }
        return metadata;
    }

    /**
     * 注册 Modules
     */
    private void registerModules() {
        log.info(Lang.WAIT_FOR_MODULES_STARTUP);
        moduleManager.register(IPBlackList.class);
        moduleManager.register(PeerIdBlacklist.class);
        moduleManager.register(ClientNameBlacklist.class);
        moduleManager.register(ExpressionRule.class);
        moduleManager.register(ProgressCheatBlocker.class);
        moduleManager.register(MultiDialingBlocker.class);
        //moduleManager.register(new ActiveProbing(this, profile));
        moduleManager.register(AutoRangeBan.class);
        moduleManager.register(BtnNetworkOnline.class);
        moduleManager.register(DownloaderCIDRBlockList.class);
        moduleManager.register(IPBlackRuleList.class);
        moduleManager.register(PBHMetricsController.class);
        moduleManager.register(PBHBanController.class);
        moduleManager.register(PBHMetadataController.class);
        moduleManager.register(PBHDownloaderController.class);
        moduleManager.register(RuleSubController.class);
        moduleManager.register(PBHAuthenticateController.class);
        moduleManager.register(PBHLogsController.class);
    }

    public Map<Downloader, Map<Torrent, List<Peer>>> collectPeers() {
        Map<Downloader, Map<Torrent, List<Peer>>> peers = new HashMap<>();
        try (var service = Executors.newVirtualThreadPerTaskExecutor()) {
            downloaders.forEach(downloader -> service.submit(() -> {
                try {
                    Map<Torrent, List<Peer>> p = collectPeers(downloader);
                    peers.put(downloader, p);
                } catch (Exception e) {
                    log.error(Lang.DOWNLOADER_UNHANDLED_EXCEPTION, e);
                }
            }));
        }
        return peers;
    }

    public Map<Torrent, List<Peer>> collectPeers(Downloader downloader) {
        Map<Torrent, List<Peer>> peers = new ConcurrentHashMap<>();
        if (!downloader.login()) {
            log.error(Lang.ERR_CLIENT_LOGIN_FAILURE_SKIP, downloader.getName(), downloader.getEndpoint());
            downloader.setLastStatus(DownloaderLastStatus.ERROR, Lang.STATUS_TEXT_LOGIN_FAILED);
            return Collections.emptyMap();
        }
        List<Torrent> torrents = downloader.getTorrents();
        Semaphore parallelReqRestrict = new Semaphore(16);
        try (TimeoutProtect protect = new TimeoutProtect(ExceptedTime.COLLECT_PEERS.getTimeout(), (t) -> {
            log.error(Lang.TIMING_COLLECT_PEERS);
        })) {
            torrents.forEach(torrent -> protect.getService().submit(() -> {
                try {
                    parallelReqRestrict.acquire();
                    peers.put(torrent, downloader.getPeers(torrent));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    parallelReqRestrict.release();
                }
            }));
            downloader.setLastStatus(DownloaderLastStatus.HEALTHY, Lang.STATUS_TEXT_OK);
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
    public CheckResult checkBan(@NotNull Torrent torrent, @NotNull Peer peer) {
        List<CheckResult> results = new ArrayList<>();
        if (peer.getPeerAddress().getAddress().isAnyLocal()) {
            return new CheckResult(getClass(), PeerAction.SKIP, 0, "local access", "skip local network peers");
        }
        for (IPAddress ignoreAddress : ignoreAddresses) {
            if (ignoreAddress.contains(peer.getPeerAddress().getAddress())) {
                return new CheckResult(getClass(), PeerAction.SKIP, 0, "ignored addresses", "skip peers from ignored addresses");
            }
        }
        try {
            for (FeatureModule registeredModule : moduleManager.getModules()) {
                if (!(registeredModule instanceof RuleFeatureModule module)) {
                    continue;
                }
                try {
                    CheckResult checkResult;
                    if (module.isThreadSafe()) {
                        checkResult = module.shouldBanPeer(torrent, peer, executor);
                    } else {
                        registeredModule.getThreadLock().lock();
                        try {
                            checkResult = module.shouldBanPeer(torrent, peer, executor);
                        } finally {
                            registeredModule.getThreadLock().unlock();
                        }
                    }
                    if (checkResult.action() == PeerAction.SKIP) {
                        results.add(checkResult);
                    }
                    results.add(checkResult);
                } catch (Exception e) {
                    log.error("Unable to execute module {}, report to PeerBanHelper developer!", module.getName(), e);
                }
            }
            CheckResult result = NO_MATCHES_CHECK_RESULT;
            for (CheckResult r : results) {
                if (r.action() == PeerAction.SKIP) {
                    result = r;
                    break; // 立刻离开循环，处理跳过
                }
                if (r.action() == PeerAction.BAN) {
                    result = r;
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to execute modules", e);
            return new CheckResult(getClass(), PeerAction.NO_ACTION, 0, "ERROR", "ERROR");
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
     * @param peer        对等体 IP 地址
     * @param banMetadata 封禁元数据
     */
    private void banPeer(@NotNull BanMetadata banMetadata, @NotNull Torrent torrentObj, @NotNull Peer peer) {
        BAN_LIST.put(peer.getPeerAddress(), banMetadata);
        metrics.recordPeerBan(peer.getPeerAddress(), banMetadata);
        banListInvoker.forEach(i -> i.add(peer.getPeerAddress(), banMetadata));
        if (mainConfig.getBoolean("lookup.dns-reverse-lookup")) {
            executor.submit(() -> banMetadata.setReverseLookup(peer.getPeerAddress().getAddress().toReverseDNSLookupString()));
        } else {
            banMetadata.setReverseLookup("N/A");
        }
        Main.getEventBus().post(new PeerBanEvent(peer.getPeerAddress(), banMetadata, torrentObj, peer));
    }

    public void scheduleBanPeer(@NotNull BanMetadata banMetadata, @NotNull Torrent torrent, @NotNull Peer peer) {
        scheduledPeerBannings.add(new ScheduledPeerBanning(banMetadata, torrent, peer));
    }

    public void scheduleUnBanPeer(@NotNull PeerAddress peer) {
        scheduledPeerBannings.removeIf(s -> s.banMetadata().getPeer().getAddress().getIp().equals(peer.getIp()));
        scheduledPeerUnBannings.add(peer);
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

    public Map<PeerAddress, PeerMetadata> getLivePeersSnapshot() {
        return LIVE_PEERS;
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
            BanMetadata banMetadata,
            Torrent torrent,
            Peer peer
    ) {
    }
}
