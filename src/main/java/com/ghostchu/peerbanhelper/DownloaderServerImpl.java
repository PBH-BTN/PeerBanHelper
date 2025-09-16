package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.peer.PeerImpl;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.bittorrent.torrent.TorrentImpl;
import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.dao.impl.BanListDao;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult;
import com.ghostchu.peerbanhelper.downloader.DownloaderManagerImpl;
import com.ghostchu.peerbanhelper.event.BanWaveLifeCycleEvent;
import com.ghostchu.peerbanhelper.event.LivePeersUpdatedEvent;
import com.ghostchu.peerbanhelper.event.PeerBanEvent;
import com.ghostchu.peerbanhelper.event.PeerUnbanEvent;
import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.module.*;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.ghostchu.peerbanhelper.util.WatchDog;
import com.ghostchu.peerbanhelper.util.dns.DNSLookup;
import com.ghostchu.peerbanhelper.util.lab.Experiments;
import com.ghostchu.peerbanhelper.util.lab.Laboratory;
import com.ghostchu.peerbanhelper.util.time.ExceptedTime;
import com.ghostchu.peerbanhelper.util.time.TimeoutProtect;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.wrapper.PeerMetadata;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.j256.ormlite.misc.TransactionManager;
import inet.ipaddr.IPAddress;
import inet.ipaddr.format.util.AssociativeAddressTrie;
import inet.ipaddr.format.util.DualIPv4v6Tries;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public final class DownloaderServerImpl implements Reloadable, AutoCloseable, DownloaderServer {
    @Getter
    private final DualIPv4v6Tries ignoreAddresses = new DualIPv4v6Tries();
    private final Lock banWaveLock = new ReentrantLock();
    private final BanList banList;
    private final Deque<ScheduledBanListOperation> scheduledBanListOperations = new ConcurrentLinkedDeque<>();
    private final DownloaderManagerImpl downloaderManager;
    private final BasicMetrics metrics;
    private final ModuleManagerImpl moduleManager;
    @Getter
    private long banDuration;
    private Map<PeerAddress, List<PeerMetadata>> LIVE_PEERS = Collections.synchronizedMap(new HashMap<>());
    @Getter
    private boolean hideFinishLogs;
    private static final long BANLIST_SAVE_INTERVAL = 60 * 60 * 1000;
    private final CheckResult NO_MATCHES_CHECK_RESULT = new CheckResult(getClass(), PeerAction.NO_ACTION, 0, new TranslationComponent("No Matches"), new TranslationComponent("No Matches"), StructuredData.create());
    @Getter
    private final AtomicBoolean needReApplyBanList = new AtomicBoolean();
    private ScheduledExecutorService BAN_WAVE_SERVICE;
    private WatchDog banWaveWatchDog;
    private final BanListDao banListDao;
    private final DNSLookup dnsLookup;
    private final Laboratory laboratory;
    @Getter
    private boolean globalPaused = false;
    private final AlertManager alertManager;
    private final Database databaseManager;
    private final ExecutorService parallelService = Executors.newWorkStealingPool();


    public DownloaderServerImpl(BanList banList, DownloaderManagerImpl downloaderManager,
                                @Qualifier("persistMetrics") BasicMetrics metrics,
                                ModuleManagerImpl moduleManager, BanListDao banListDao,
                                DNSLookup dnsLookup, Laboratory laboratory,
                                AlertManager alertManager, Database databaseManager) {
        this.banList = banList;
        this.downloaderManager = downloaderManager;
        this.metrics = metrics;
        this.banListDao = banListDao;
        this.dnsLookup = dnsLookup;
        this.moduleManager = moduleManager;
        this.laboratory = laboratory;
        this.alertManager = alertManager;
        this.databaseManager = databaseManager;
        Main.getReloadManager().register(this);
        loadBanListToMemory();
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        load();
        return Reloadable.super.reloadModule();
    }

    public void load() {
        this.banDuration = Main.getProfileConfig().getLong("ban-duration");
        this.hideFinishLogs = Main.getMainConfig().getBoolean("logger.hide-finish-log");
        Main.getProfileConfig().getStringList("ignore-peers-from-addresses").forEach(ip -> {
            IPAddress ignored = IPAddressUtil.getIPAddress(ip);
            ignoreAddresses.add(ignored);
        });
        reApplyBanListForDownloaders();
        unbanWhitelistedPeers();
        registerTimer();
    }


    @Override
    public void close() {
        this.metrics.close();
        saveBanList();
    }

    private void unbanWhitelistedPeers() {
        banList.forEach((addr, meta) -> {
                    var node = ignoreAddresses.elementsContaining(addr);
                    if (node != null) {
                        scheduleUnBanPeer(addr);
                    }
                }
        );
    }

    private void loadBanListToMemory() {
        if (!Main.getMainConfig().getBoolean("persist.banlist")) {
            return;
        }
        this.banList.reset();
        try {
            Map<IPAddress, BanMetadata> data = banListDao.readBanList();
            this.banList.addAll(data);
            log.info(tlUI(Lang.LOAD_BANLIST_FROM_FILE, data.size()));
            Thread.startVirtualThread(this::reApplyBanListForDownloaders);
        } catch (Exception e) {
            log.error(tlUI(Lang.ERR_UPDATE_BAN_LIST), e);
        }
    }

    private void saveBanList() {
        if (!Main.getMainConfig().getBoolean("persist.banlist")) {
            return;
        }
        try {
            int count = banListDao.saveBanList(banList);
            log.info(tlUI(Lang.SAVED_BANLIST, count));
        } catch (Exception e) {
            log.error(tlUI(Lang.SAVE_BANLIST_FAILED), e);
        }
    }

    /**
     * 启动新的一轮封禁序列
     */
    public void banWave() {
        try {
            if (!banWaveLock.tryLock(3, TimeUnit.SECONDS)) {
                return;
            }
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.STARTING));
            if (isGlobalPaused()) {
                if (needReApplyBanList.get()) {
                    Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.PRE_REAPPLY_BAN_LIST));
                    reApplyBanListForDownloaders();
                    Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.POST_REAPPLY_BAN_LIST));
                }
                return;
            }
            banWaveWatchDog.setLastOperation("Ban wave - start");
            long startTimer = System.currentTimeMillis();
            // 重置所有下载器状态为健康，这样后面失败就会对其降级
            banWaveWatchDog.setLastOperation("Reset last status");
            // 声明基本集合
            // 执行计划任务
            banWaveWatchDog.setLastOperation("Run scheduled tasks");
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.PRE_EXECUTE_DOWNLOADER_SCHEDULED_TASKS));
            downloaderManager.forEach(Downloader::runScheduleTasks);
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.POST_EXECUTE_DOWNLOADER_SCHEDULED_TASKS));
            // 被解除封禁的对等体列表
            banWaveWatchDog.setLastOperation("Remove expired bans");
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.PRE_REMOVE_EXPIRED_BANS));
            Collection<BanMetadata> unbannedPeers = removeExpiredBans();
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.POST_REMOVE_EXPIRED_BANS));
            // 被新封禁的对等体列表
            Collection<BanMetadata> bannedPeers = new CopyOnWriteArrayList<>();
            // 当前所有活跃的对等体列表
            banWaveWatchDog.setLastOperation("Collect peers");
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.PRE_COLLECT_PEERS));
            Map<Downloader, Map<Torrent, List<Peer>>> peers = collectPeers();
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.POST_COLLECT_PEERS));
            // 更新 LIVE_PEERS 用于数据展示
            banWaveWatchDog.setLastOperation("Update live peers");
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.PRE_UPDATE_LIVE_PEERS));
            updateLivePeers(peers);
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.POST_UPDATE_LIVE_PEERS));
            banWaveWatchDog.setLastOperation("Notify BatchMonitorFeatureModules");
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.PRE_NOTIFY_BATCH_MONITOR_MODULES));
            for (FeatureModule module : moduleManager.getModules()) {
                if (module instanceof BatchMonitorFeatureModule batchMonitorFeatureModule) {
                    batchMonitorFeatureModule.onPeersRetrieved(peers);
                }
            }
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.POST_NOTIFY_BATCH_MONITOR_MODULES));
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.PRE_NOTIFY_MONITOR_MODULES));
            peers.forEach((downloader, entry) -> {
                banWaveWatchDog.setLastOperation("Notify MonitorFeatureModules");
                for (FeatureModule module : moduleManager.getModules()) {
                    if (module instanceof MonitorFeatureModule monitorFeatureModule) {
                        entry.forEach((torrent, plist) -> monitorFeatureModule.onTorrentPeersRetrieved(downloader, torrent, plist));
                    }
                }
            });
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.POST_NOTIFY_MONITOR_MODULES));
            // ========== 处理封禁逻辑 ==========
            Map<Downloader, List<BanDetail>> downloaderBanDetailMap = new ConcurrentHashMap<>();
            banWaveWatchDog.setLastOperation("Check Bans");
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.PRE_CHECK_BANS));
            try (TimeoutProtect protect = new TimeoutProtect(ExceptedTime.CHECK_BANS.getTimeout(), (t) -> log.error(tlUI(Lang.TIMING_CHECK_BANS)))) {
                peers.keySet().forEach(downloader -> protect.getService().submit(() -> {
                    try {
                        downloaderBanDetailMap.put(downloader, checkBans(peers.get(downloader), downloader));
                    } catch (Exception e) {
                        log.error("Unexpected fatal error occurred while checking bans!", e);
                        throw e;
                    }
                }));
            }
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.POST_CHECK_BANS));
            // 处理计划操作
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.PRE_PROCESS_SCHEDULED_TASKS));
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
                    var address = (PeerAddress) ops.object();
                    BanMetadata banMetadata = banList.get(address);
                    if (banMetadata != null) {
                        unbannedPeers.add(banMetadata);
                    }
                }
            }

            if (scheduled > 0) {
                log.info(tlUI(Lang.SCHEDULED_OPERATIONS, scheduled));
            }
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.POST_PROCESS_SCHEDULED_TASKS));
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.PRE_HANDLE_BAN_ENTRIES));
            // 添加被封禁的 Peers 到封禁列表中
            banWaveWatchDog.setLastOperation("Add banned peers into banlist");
            try (TimeoutProtect protect = new TimeoutProtect(ExceptedTime.ADD_BAN_ENTRY.getTimeout(), (t) -> log.error(tlUI(Lang.TIMING_ADD_BANS)))) {
                Callable<Object> callable = () -> {
                    var banlistClone = banList.copyKeySet();
                    downloaderBanDetailMap.forEach((downloader, details) -> {
                        try {
                            details.forEach(detail -> protect.getService().submit(() -> {
                                try {
                                    if (detail.result().action() == PeerAction.BAN || detail.result().action() == PeerAction.BAN_FOR_DISCONNECT) {
                                        long actualBanDuration = banDuration;
                                        if (detail.banDuration() > 0) {
                                            actualBanDuration = detail.banDuration();
                                        }
                                        BanMetadata banMetadata = new BanMetadata(detail.result().moduleContext().getName(),
                                                UUID.randomUUID().toString().replace("-", "")
                                                , downloaderManager.getDownloadInfo(downloader.getId()),
                                                System.currentTimeMillis(), System.currentTimeMillis() + actualBanDuration,
                                                detail.result().action() == PeerAction.BAN_FOR_DISCONNECT,
                                                detail.result().action() == PeerAction.BAN_FOR_DISCONNECT,
                                                detail.result().action() == PeerAction.BAN_FOR_DISCONNECT,
                                                detail.torrent(), detail.peer(), detail.result().rule(), detail.result().reason(), detail.result().structuredData());
                                        bannedPeers.add(banMetadata);
                                        banPeer(banlistClone, banMetadata, detail.torrent(), detail.peer());
                                        if (detail.result().action() != PeerAction.BAN_FOR_DISCONNECT) {
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
                                } catch (Exception e) {
                                    log.error(tlUI(Lang.BAN_PEER_EXCEPTION), e);
                                }
                            }));
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
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.POST_HANDLE_BAN_ENTRIES));
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.PRE_APPLY_BAN_LIST));
            banWaveWatchDog.setLastOperation("Apply banlist");
            // 如果需要，则应用更改封禁列表到下载器
            try (TimeoutProtect protect = new TimeoutProtect(ExceptedTime.APPLY_BANLIST.getTimeout(), (t) -> log.error(tlUI(Lang.TIMING_APPLY_BAN_LIST)))) {
                if (!needReApplyBanList.get()) {
                    downloaderManager.forEach(downloader -> protect.getService().submit(() ->
                            updateDownloader(downloader, !bannedPeers.isEmpty() || !unbannedPeers.isEmpty(), bannedPeers, unbannedPeers, false)));
                } else {
                    log.info(tlUI(Lang.APPLYING_FULL_BANLIST_TO_DOWNLOADER));
                    downloaderManager.forEach(downloader -> protect.getService().submit(() -> updateDownloader(downloader, true, null, null, true)));
                    needReApplyBanList.set(false);
                }
            }
            if (!hideFinishLogs && !downloaderManager.isEmpty()) {
                long downloadersCount = peers.size();
                long torrentsCount = peers.values().stream().mapToLong(Map::size).sum();
                long peersCount = peers.values().stream().flatMap(e -> e.values().stream()).mapToLong(List::size).sum();
                log.info(tlUI(Lang.BAN_WAVE_CHECK_COMPLETED, downloadersCount, torrentsCount, peersCount, bannedPeers.size(), unbannedPeers.size(), System.currentTimeMillis() - startTimer));
            }
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.POST_APPLIED_BAN_LIST));
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
            Main.getEventBus().post(new BanWaveLifeCycleEvent(BanWaveLifeCycleEvent.Stage.ENDED));
        }
    }

    private void reApplyBanListForDownloaders() {
        downloaderManager.forEach(downloader -> {
            if (downloader.login().success()) {
                downloader.setBanList(banList.copyKeySet(), null, null, true);
            }
        });
    }

    private List<BanDetail> checkBans(Map<Torrent, List<Peer>> provided, @NotNull Downloader downloader) {
        List<BanDetail> details = Collections.synchronizedList(new ArrayList<>());
        try (TimeoutProtect protect = new TimeoutProtect(ExceptedTime.CHECK_BANS.getTimeout(), (t) -> log.error(tlUI(Lang.TIMING_CHECK_BANS)))) {
            Semaphore semaphore = new Semaphore(Math.min(Runtime.getRuntime().availableProcessors(), ExternalSwitch.parseInt("pbh.checkParallelism", 32)));
            for (Torrent torrent : provided.keySet()) {
                List<Peer> peers = provided.get(torrent);
                for (Peer peer : peers) {
                    protect.getService().submit(() -> {
                        try {
                            semaphore.acquire();
                            CheckResult checkResult = checkBan(torrent, peer, downloader);
                            details.add(new BanDetail(torrent, peer, checkResult, checkResult.duration()));
                        } catch (Exception e) {
                            log.error("Unexpected error occurred while checking bans", e);
                        } finally {
                            semaphore.release();
                        }
                    });
                }
            }
        }
        return details;
    }

    private void updateLivePeers(Map<Downloader, Map<Torrent, List<Peer>>> peers) {
        Map<PeerAddress, List<PeerMetadata>> livePeers = new HashMap<>();
        peers.forEach((downloader, tasks) ->
                tasks.forEach((torrent, peer) ->
                        peer.forEach(p -> {
                                    PeerAddress address = p.getPeerAddress();
                                    List<PeerMetadata> data = livePeers.getOrDefault(address, new ArrayList<>());
                                    PeerMetadata metadata = new PeerMetadata(
                                            downloaderManager.getDownloadInfo(downloader),
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
     * @param downloader    要操作的下载器
     * @param updateBanList 是否需要从 BAN_LIST 常量更新封禁列表到下载器
     */
    public void updateDownloader(@NotNull Downloader downloader, boolean updateBanList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList) {
        if (!updateBanList) return;
        try {
            var loginResult = downloader.login();
            if (!loginResult.success()) {
                if (loginResult.status() != DownloaderLoginResult.Status.PAUSED) {
                    log.error(tlUI(Lang.ERR_CLIENT_LOGIN_FAILURE_SKIP, downloader.getName(), downloader.getEndpoint(), tlUI(loginResult.message())));
                    downloader.setLastStatus(DownloaderLastStatus.ERROR, loginResult.message());
                }
                return;
            } else {
                downloader.setLastStatus(DownloaderLastStatus.HEALTHY, loginResult.message());
            }
            downloader.setBanList(banList.copyKeySet(), added, removed, applyFullList);
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
        List<IPAddress> removeBan = new ArrayList<>();
        List<BanMetadata> metadata = new ArrayList<>();
        banList.forEach((key, value) -> {
            if (System.currentTimeMillis() >= value.getUnbanAt()) {
                removeBan.add(key);
                metadata.add(value);
            }
        });
        removeBan.forEach(this::unbanPeer);
        long normalUnbanCount = metadata.stream().filter(meta -> !meta.isBanForDisconnect()).count();
        if (normalUnbanCount > 0) {
            log.info(tlUI(Lang.PEER_UNBAN_WAVE, normalUnbanCount));
        }
        return metadata;
    }


    public Map<Downloader, Map<Torrent, List<Peer>>> collectPeers() {
        Map<Downloader, Map<Torrent, List<Peer>>> peers = Collections.synchronizedMap(new HashMap<>());
        for (CompletableFuture<Void> future : downloaderManager.stream().map(downloader -> CompletableFuture.runAsync(() -> {
            try {
                Map<Torrent, List<Peer>> p = collectPeers(downloader);
                if (p != null) {
                    peers.put(downloader, p);
                }
            } catch (Exception e) {
                log.error(tlUI(Lang.DOWNLOADER_UNHANDLED_EXCEPTION), e);
            }
        }, parallelService)).toList()) {
            future.join();
        }
        return peers;
    }

    @Nullable
    public Map<Torrent, List<Peer>> collectPeers(Downloader downloader) {
        Map<Torrent, List<Peer>> peers = Collections.synchronizedMap(new HashMap<>());
        var loginResult = downloader.login();
        if (!loginResult.success()) {
            if (loginResult.status() != DownloaderLoginResult.Status.PAUSED) {
                log.error(tlUI(Lang.ERR_CLIENT_LOGIN_FAILURE_SKIP, downloader.getName(), downloader.getEndpoint(), tlUI(loginResult.message())));
                downloader.setLastStatus(DownloaderLastStatus.ERROR, loginResult.message());
                if (loginResult.status() == DownloaderLoginResult.Status.MISSING_COMPONENTS || loginResult.status() == DownloaderLoginResult.Status.REQUIRE_TAKE_ACTIONS) {
                    downloader.setLastStatus(DownloaderLastStatus.NEED_TAKE_ACTION, loginResult.message());
                }
            }
            return null;
        }
        List<Torrent> torrents = downloader.getTorrents();
        Semaphore parallelReqRestrict = new Semaphore(downloader.getMaxConcurrentPeerRequestSlots());
        try (TimeoutProtect protect = new TimeoutProtect(ExceptedTime.COLLECT_PEERS.getTimeout(), (t) -> log.error(tlUI(Lang.TIMING_COLLECT_PEERS)))) {
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
            if (isPeerHavePossibleBadNatConfig(peer)) {
                if (!alertManager.identifierAlertExistsIncludeRead("downloader-nat-setup-error@" + downloader.getId())) {
                    alertManager.publishAlert(true, AlertLevel.ERROR, "downloader-nat-setup-error@" + downloader.getId(),
                            new TranslationComponent(Lang.DOWNLOADER_DOCKER_INCORRECT_NETWORK_DETECTED_TITLE),
                            new TranslationComponent(Lang.DOWNLOADER_DOCKER_INCORRECT_NETWORK_DETECTED_DESCRIPTION, downloader.getId(), peer.getPeerAddress().getAddress().toNormalizedString()));
                }
            }
            return new CheckResult(getClass(), PeerAction.SKIP, 0, new TranslationComponent("general-rule-ignored-address"), new TranslationComponent("general-reason-skip-ignored-peers"), StructuredData.create().add("type", "ignoredAddresses"));
        }
        try {
            for (FeatureModule registeredModule : moduleManager.getModules()) {
                if (!(registeredModule instanceof RuleFeatureModule module)) {
                    continue;
                }
                try {
                    CheckResult checkResult;
                    if (module.isThreadSafe()) {
                        checkResult = module.shouldBanPeer(torrent, peer, downloader);
                    } else {
                        registeredModule.getThreadLock().lock();
                        try {
                            checkResult = module.shouldBanPeer(torrent, peer, downloader);
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
                if (r.action() == PeerAction.BAN || r.action() == PeerAction.BAN_FOR_DISCONNECT) {
                    result = r;
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to execute modules", e);
            return new CheckResult(getClass(), PeerAction.NO_ACTION, 0,
                    new TranslationComponent("ERROR"),
                    new TranslationComponent("ERROR"),
                    StructuredData.create().add("message", e.getMessage())
                            .add("class", e.getClass().getName())
                            .add("stackTrace", e.getStackTrace()));
        }
    }

    private boolean isPeerHavePossibleBadNatConfig(Peer peer) {
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
                return (addrStr.endsWith(".1") || addrStr.endsWith(".0")) && (addr.isLocal() || addr.isAnyLocal());
            }
        }
        return false;
    }


    @Override
    public @NotNull Map<PeerAddress, List<PeerMetadata>> getPeerSnapshot() {
        return LIVE_PEERS;
    }

    /**
     * 以指定元数据封禁一个特定的对等体
     *
     * @param compareWith 对比 BanList，默认 BAN_LIST 或者 BAN_LIST 的克隆
     * @param peer        对等体 IP 地址
     * @param banMetadata 封禁元数据
     */
    private void banPeer(@NotNull Set<IPAddress> compareWith, @NotNull BanMetadata banMetadata, @NotNull Torrent torrentObj, @NotNull Peer peer) {
        if (compareWith.contains(peer.getPeerAddress().getAddress())) {
            log.error(tlUI(Lang.DUPLICATE_BAN, banMetadata));
            needReApplyBanList.set(true);
            log.warn(tlUI(Lang.SCHEDULED_FULL_BANLIST_APPLY));
        }
        banList.add(peer.getPeerAddress(), banMetadata);
        metrics.recordPeerBan(peer.getPeerAddress().getAddress(), banMetadata);
        banMetadata.setReverseLookup("N/A");
        if (Main.getMainConfig().getBoolean("lookup.dns-reverse-lookup")) {
            Thread.ofVirtual().start(() -> {
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

    @Override
    public void scheduleBanPeerNoAssign(@NotNull BanMetadata banMetadata, @NotNull Torrent torrent, @NotNull Peer peer) {
        Downloader downloader = downloaderManager.stream().filter(d -> d.getId().equals(banMetadata.getDownloader().id()))
                .findFirst().orElseThrow();
        banPeer(banList.copyKeySet(), banMetadata, torrent, peer);
        scheduledBanListOperations.add(new ScheduledBanListOperation(true, new ScheduledPeerBanning(
                downloader,
                new BanDetail(torrent,
                        peer,
                        new CheckResult(getClass(), PeerAction.BAN, banDuration,
                                new TranslationComponent(Lang.PEER_BAN_USER_OPERATE_TITLE),
                                new TranslationComponent(Lang.PEER_BAN_USER_OPERATE_DESCRIPTION),
                                StructuredData.create().add("type", "manually"))
                        , banDuration)
        )));
    }

    @Override
    public void scheduleBanPeerNoAssign(@NotNull PeerAddress addr) {
        String mockTorrentHash = "00000000000000000000";
        Torrent torrent = new TorrentImpl(mockTorrentHash, "User Operation", mockTorrentHash, 0, 0, 0.0d, 0, 0, false);
        Peer peer = new PeerImpl(addr,
                "-USROPS-".getBytes(StandardCharsets.ISO_8859_1),
                "User Operation",
                0, 0, 0, 0, 0, null, false);
        long yearPlus100 = 100L * 365 * 24 * 60 * 60 * 1000;
        scheduledBanListOperations.add(new ScheduledBanListOperation(true, new ScheduledPeerBanning(
                downloaderManager.getDownloaders().getFirst(),
                new BanDetail(torrent,
                        peer,
                        new CheckResult(getClass(), PeerAction.BAN, banDuration,
                                new TranslationComponent(Lang.PEER_BAN_USER_OPERATE_TITLE),
                                new TranslationComponent(Lang.PEER_BAN_USER_OPERATE_DESCRIPTION, addr),
                                StructuredData.create().add("type", "manually").add("ip", addr))
                        , yearPlus100)
        )));
    }

    @Override
    public void scheduleUnBanPeer(@NotNull PeerAddress peer) {
        unbanPeer(peer.getAddress());
        scheduledBanListOperations.add(new ScheduledBanListOperation(false, peer));
    }

    @Override
    public void scheduleUnBanPeer(@NotNull IPAddress peer) {
        unbanPeer(peer);
        scheduledBanListOperations.add(new ScheduledBanListOperation(false, new PeerAddress(peer.toNormalizedString(), 0, peer.toNormalizedString())));
    }


    /**
     * 解除一个指定对等体
     *
     * @param address 对等体 IP 地址
     * @return 此对等体的封禁元数据；返回 null 代表此对等体没有被封禁
     */
    private AssociativeAddressTrie.@Nullable AssociativeTrieNode<? extends IPAddress, BanMetadata> unbanPeer(@NotNull IPAddress address) {
        var metadata = banList.remove(address);
        if (metadata != null) {
            metrics.recordPeerUnban(address, metadata.getValue());
            Main.getEventBus().post(new PeerUnbanEvent(address, metadata.getValue()));
        }
        return metadata;
    }

    @Override
    public Map<PeerAddress, List<PeerMetadata>> getLivePeersSnapshot() {
        return LIVE_PEERS;
    }


    private void registerTimer() {
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::saveBanList, 10 * 1000, BANLIST_SAVE_INTERVAL, TimeUnit.MILLISECONDS);
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

    @Override
    public @NotNull BanList getBanList() {
        return banList;
    }

    private void watchDogHungry() {
        StringBuilder threadDump = new StringBuilder(System.lineSeparator());
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(true, true)) {
            threadDump.append(MsgUtil.threadInfoToString(threadInfo));
        }
        threadDump.append("\n\n");
        var deadLockedThreads = threadMXBean.findDeadlockedThreads();
        var monitorDeadlockedThreads = threadMXBean.findMonitorDeadlockedThreads();
        if (deadLockedThreads != null) {
            threadDump.append("Deadlocked Threads:\n");
            for (ThreadInfo threadInfo : threadMXBean.getThreadInfo(deadLockedThreads)) {
                threadDump.append(MsgUtil.threadInfoToString(threadInfo));
            }
        }
        if (monitorDeadlockedThreads != null) {
            threadDump.append("Monitor Deadlocked Threads:\n");
            for (ThreadInfo threadInfo : threadMXBean.getThreadInfo(monitorDeadlockedThreads)) {
                threadDump.append(MsgUtil.threadInfoToString(threadInfo));
            }
        }
        log.info(threadDump.toString());
        registerBanWaveTimer();
        Main.getGuiManager().createNotification(Level.WARN, tlUI(Lang.BAN_WAVE_WATCH_DOG_TITLE), tlUI(Lang.BAN_WAVE_WATCH_DOG_DESCRIPTION));
    }


    @Override
    public void setGlobalPaused(boolean globalPaused) {
        this.globalPaused = globalPaused;
        if (globalPaused) {
            ExchangeMap.GUI_DISPLAY_FLAGS.add(new ExchangeMap.DisplayFlag("global-paused", 20, tlUI(Lang.STATUS_BAR_GLOBAL_PAUSED)));
        } else {
            ExchangeMap.GUI_DISPLAY_FLAGS.removeIf(f -> "global-paused".equals(f.getId()));
        }
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
