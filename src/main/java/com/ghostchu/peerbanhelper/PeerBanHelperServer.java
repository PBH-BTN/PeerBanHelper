package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.btn.BtnManager;
import com.ghostchu.peerbanhelper.database.DatabaseHelper;
import com.ghostchu.peerbanhelper.database.DatabaseManager;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.invoker.BanListInvoker;
import com.ghostchu.peerbanhelper.invoker.impl.CommandExec;
import com.ghostchu.peerbanhelper.invoker.impl.IPFilterInvoker;
import com.ghostchu.peerbanhelper.metric.Metrics;
import com.ghostchu.peerbanhelper.metric.impl.persist.PersistMetrics;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.FeatureModule;
import com.ghostchu.peerbanhelper.module.ModuleManager;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.module.impl.rule.*;
import com.ghostchu.peerbanhelper.module.impl.webapi.*;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.web.WebManager;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class PeerBanHelperServer {
    private final Map<PeerAddress, BanMetadata> BAN_LIST = new ConcurrentHashMap<>();
    private final ScheduledExecutorService BAN_WAVE_SERVICE = Executors.newScheduledThreadPool(1);
    private final YamlConfiguration profile;
    @Getter
    private final List<Downloader> downloaders;
    @Getter
    private final long banDuration;
    @Getter
    private final int httpdPort;
    @Getter
    private final boolean hideFinishLogs;
    @Getter
    private final YamlConfiguration mainConfig;
    private final ExecutorService ruleExecuteExecutor;
    @Getter
    private final BtnManager btnManager;
    @Getter
    private WebManager webManagerServer;
    private final ExecutorService generalExecutor;
    private final ExecutorService checkBanExecutor;
    private final ExecutorService downloaderApiExecutor;
    @Getter
    private Metrics metrics;
    private DatabaseManager databaseManager;
    @Getter
    private DatabaseHelper databaseHelper;
    @Getter
    private ModuleManager moduleManager;
    @Getter
    private List<BanListInvoker> banListInvoker = new ArrayList<>();

    public PeerBanHelperServer(List<Downloader> downloaders, YamlConfiguration profile, YamlConfiguration mainConfig) throws SQLException {
        this.downloaders = downloaders;
        this.profile = profile;
        this.banDuration = profile.getLong("ban-duration");
        this.mainConfig = mainConfig;
        this.httpdPort = mainConfig.getInt("server.http");
        this.generalExecutor = Executors.newWorkStealingPool(mainConfig.getInt("threads.general-parallelism", 6));
        this.checkBanExecutor = Executors.newWorkStealingPool(mainConfig.getInt("threads.check-ban-parallelism", 8));
        this.ruleExecuteExecutor = Executors.newWorkStealingPool(mainConfig.getInt("threads.rule-execute-parallelism", 16));
        this.downloaderApiExecutor = Executors.newWorkStealingPool(mainConfig.getInt("threads.downloader-api-parallelism", 8));
        this.hideFinishLogs = mainConfig.getBoolean("logger.hide-finish-log");
        registerHttpServer();
        this.moduleManager = new ModuleManager();
        BtnManager btnm;
        try {
            log.info(Lang.BTN_NETWORK_CONNECTING);
            btnm = new BtnManager(this, mainConfig.getConfigurationSection("btn"));
            log.info(Lang.BTN_NETWORK_ENABLED);
        }catch (IllegalStateException e){
            btnm = null;
            log.info(Lang.BTN_NETWORK_NOT_ENABLED);
        }
        this.btnManager = btnm;
        try {
            prepareDatabase();
        } catch (Exception e) {
            log.error(Lang.DATABASE_FAILURE, e);
            throw e;
        }
        registerMetrics();
        registerModules();
        registerTimer();
        registerBanListInvokers();
        banListInvoker.forEach(BanListInvoker::reset);
    }

    private void registerBanListInvokers() {
        banListInvoker.add(new IPFilterInvoker(this));
        banListInvoker.add(new CommandExec(this));
    }

    public void shutdown() {
        // place some clean code here
        this.metrics.close();
        this.moduleManager.unregisterAll();
        this.databaseManager.close();
        this.webManagerServer.stop();
        this.checkBanExecutor.shutdown();
        this.ruleExecuteExecutor.shutdown();
        this.downloaderApiExecutor.shutdown();
        this.generalExecutor.shutdown();
        this.downloaders.forEach(d -> {
            try {
                d.close();
            } catch (Exception e) {
                log.error("Failed to close download {}", d.getName(), e);
            }
        });
    }

    private void prepareDatabase() throws SQLException {
        this.databaseManager = new DatabaseManager(this);
        this.databaseHelper = new DatabaseHelper(databaseManager);
    }

    private void registerMetrics() {
        this.metrics = new PersistMetrics(databaseHelper);
    }

    private void registerHttpServer() {
        this.webManagerServer = new WebManager(httpdPort, this);
    }

    private void registerTimer() {
        BAN_WAVE_SERVICE.scheduleAtFixedRate(this::banWave, 1, profile.getLong("check-interval", 5000), TimeUnit.MILLISECONDS);
//        cleanupService.scheduleAtFixedRate(()->{
//            int changes = databaseHelper.cleanOutdatedBanLogs(getMainConfig().getInt("persist.ban-logs-keep-days", 30));
//            log.info(Lang.PERSIST_CLEAN_LOGS, changes);
//        }, 0, 1, TimeUnit.DAYS);
    }

    /**
     * 启动新的一轮封禁序列
     */
    public void banWave() {
        try {
            // 重置所有下载器状态为健康，这样后面失败就会对其降级
            downloaders.forEach(downloader -> downloader.setLastStatus(DownloaderLastStatus.HEALTHY));
            final AtomicBoolean needUpdateBanList = new AtomicBoolean(false);
            Map<Downloader, Collection<Torrent>> needRelaunched = new ConcurrentHashMap<>();
            // 并发处理下载器检查
            List<CompletableFuture<BanDownloaderResult>> futures = downloaders.stream().map(this::handleDownloader).toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            futures.stream().map(future -> future.getNow(null))
                    .filter(Objects::nonNull)
                    .forEach(r -> {
                        if (r.needUpdateBanList()) {
                            needUpdateBanList.set(true);
                        }
                        if (!r.torrentsAffected().isEmpty()) {
                            needUpdateBanList.set(true);
                            needRelaunched.put(r.downloader(), r.torrentsAffected());
                        }
                    });
            // 移除可能的过期封禁对等体
            if (removeExpiredBans()) {
                needUpdateBanList.set(true);
            }
            // 如果需要，则应用更改
            downloaders.forEach(downloader -> updateDownloader(downloader, needUpdateBanList.get(), needRelaunched.getOrDefault(downloader, Collections.emptyList())));
        } finally {
            metrics.recordCheck();
            System.gc(); // 要求 GraalVM 的 Native Image 立刻回收内存并释放给本机系统；对 JVM 版本没什么太大的意义
        }
    }

    /**
     * 如果需要，则更新下载器的封禁列表
     * 对于 Transmission 等下载器来说，传递 needToRelaunch 会重启对应 Torrent
     * @param downloader 要操作的下载器
     * @param updateBanList 是否需要从 BAN_LIST 常量更新封禁列表到下载器
     * @param needToRelaunch 传递一个集合，包含需要重启的种子；并非每个下载器都遵守此行为；对于 qbittorrent 等 banlist 可被实时应用的下载器来说，不会重启 Torrent
     */
    public void updateDownloader(@NotNull Downloader downloader, boolean updateBanList, @NotNull Collection<Torrent> needToRelaunch) {
        if (!updateBanList && needToRelaunch.isEmpty()) return;
        try {
            if (!downloader.login()) {
                log.warn(Lang.ERR_CLIENT_LOGIN_FAILURE_SKIP, downloader.getName(), downloader.getEndpoint());
                downloader.setLastStatus(DownloaderLastStatus.ERROR);
                return;
            }
            downloader.setBanList(BAN_LIST.keySet());
            downloader.relaunchTorrentIfNeeded(needToRelaunch);
        } catch (Throwable th) {
            log.warn(Lang.ERR_UPDATE_BAN_LIST, downloader.getName(), downloader.getEndpoint(), th);
            downloader.setLastStatus(DownloaderLastStatus.ERROR);
        }
    }

    /**
     * 准备下载器并启动检查任务
     * @param downloader 要登录和检查的下载器实例
     * @return 异步任务，返回 (1) 是否需要更新 Banlist (2) 受到影响的 Torrent 列表
     */
    public CompletableFuture<BanDownloaderResult> handleDownloader(Downloader downloader) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!downloader.login()) {
                    log.warn(Lang.ERR_CLIENT_LOGIN_FAILURE_SKIP, downloader.getName(), downloader.getEndpoint());
                    downloader.setLastStatus(DownloaderLastStatus.ERROR);
                    return null;
                }
                return banDownloader(downloader);
            } catch (Throwable th) {
                log.warn(Lang.ERR_UNEXPECTED_API_ERROR, downloader.getName(), downloader.getEndpoint(), th);
                downloader.setLastStatus(DownloaderLastStatus.ERROR);
                return null;
            }
        }, checkBanExecutor);
    }

    /**
     * 移除过期的封禁
     * @return 当封禁条目过期时，移除它们（解封禁）
     */
    public boolean removeExpiredBans() {
        List<PeerAddress> removeBan = new ArrayList<>();
        for (Map.Entry<PeerAddress, BanMetadata> pair : BAN_LIST.entrySet()) {
            if (System.currentTimeMillis() >= pair.getValue().getUnbanAt()) {
                removeBan.add(pair.getKey());
            }
        }
        removeBan.forEach(this::unbanPeer);
        if (!removeBan.isEmpty()) {
            log.info(Lang.PEER_UNBAN_WAVE, removeBan.size());
            return true;
        }
        return false;
    }

    /**
     * 注册 Modules
     */
    private void registerModules() {
        log.info(Lang.WAIT_FOR_MODULES_STARTUP);
        moduleManager.register(new IPBlackList(this, profile));
        moduleManager.register(new PeerIdBlacklist(this, profile));
        moduleManager.register(new ClientNameBlacklist(this, profile));
        moduleManager.register(new ProgressCheatBlocker(this, profile));
        moduleManager.register(new ActiveProbing(this, profile));
        moduleManager.register(new AutoRangeBan(this, profile));
        moduleManager.register(new BtnNetworkOnline(this, profile));
        moduleManager.register(new DownloaderCIDRBlockList(this, profile));
        moduleManager.register(new PBHBanList(this, profile));
        moduleManager.register(new PBHBanLogs(this, profile, databaseHelper));
        moduleManager.register(new PBHClientStatus(this, profile));
        moduleManager.register(new PBHMaxBans(this, profile, databaseHelper));
        moduleManager.register(new PBHMetrics(this, profile));
        moduleManager.register(new PBHMetadata(this, profile));
    }


    /**
     * 检查并封禁指定下载器上的对等体
     * @param downloader 要检查的下载器实例
     * @return 是否需要更新 Banlist，以及一个包含受到影响的 Torrent 的集合
     */
    @NotNull
    public BanDownloaderResult banDownloader(@NotNull Downloader downloader) {
        AtomicBoolean needUpdate = new AtomicBoolean(false);
        Map<Torrent, List<Peer>> map = new ConcurrentHashMap<>();
        Set<Torrent> needRelaunched = new CopyOnWriteArraySet<>();
        AtomicInteger peers = new AtomicInteger(0);
        // 多线程获取所有 Torrents 的 Peers
        List<CompletableFuture<?>> fetchPeerFutures = new ArrayList<>(downloader.getTorrents().size());
        downloader.getTorrents().forEach(torrent -> fetchPeerFutures.add(CompletableFuture.runAsync(() -> map.put(torrent, downloader.getPeers(torrent)), downloaderApiExecutor)));
        CompletableFuture.allOf(fetchPeerFutures.toArray(new CompletableFuture[0])).join();
        // 多线程检查是否应该封禁 Peers （以优化启用了主动探测的环境下的检查性能）
        List<CompletableFuture<?>> checkPeersBanFutures = new ArrayList<>(map.size());
        map.forEach((key, value) -> {
            peers.addAndGet(value.size());
            for (Peer peer : value) {
                checkPeersBanFutures.add(CompletableFuture.runAsync(() -> {
                    BanResult banResult = checkBan(key, peer);
                    // 跳过优先级最高
                    if (banResult.action() == PeerAction.SKIP) {
                        return;
                    }
                    if (banResult.action() == PeerAction.BAN) {
                        needUpdate.set(true);
                        needRelaunched.add(key);
                        banPeer(peer.getAddress(), new BanMetadata(banResult.moduleContext().getClass().getName(), System.currentTimeMillis(), System.currentTimeMillis() + banDuration, key, peer, banResult.reason()));
                        log.warn(Lang.BAN_PEER, peer.getAddress(), peer.getPeerId(), peer.getClientName(), peer.getProgress(), peer.getUploaded(), peer.getDownloaded(), key.getName(), banResult.reason());
                    }
                }, generalExecutor));
            }
        });
        CompletableFuture.allOf(checkPeersBanFutures.toArray(new CompletableFuture[0])).join();
        if (!hideFinishLogs) {
            log.info(Lang.CHECK_COMPLETED, downloader.getName(), map.keySet().size(), peers);
        }
        return new BanDownloaderResult(downloader, needUpdate.get(), needRelaunched);
    }

    private boolean isHandshaking(Peer peer) {
        if (peer.getPeerId() == null || peer.getPeerId().isEmpty()) {
            // 跳过此 Peer，PeerId 不能为空，此时只建立了连接，但还没有完成交换
            return true;
        }
        //noinspection RedundantIfStatement
        if (peer.getDownloadSpeed() <= 0 && peer.getUploadedSpeed() <= 0) {
            // 跳过此 Peer，速度都是0，可能是没有完成握手
            return true;
        }
        return false;
    }

    /**
     * 检查一个在给定 Torrent 上的对等体是否需要被封禁
     * @param torrent Torrent
     * @param peer 对等体
     * @return 封禁规则检查结果
     */
    @NotNull
    public BanResult checkBan(@NotNull Torrent torrent, @NotNull Peer peer) {
        Object wakeLock = new Object();
        List<CompletableFuture<Void>> moduleExecutorFutures = new ArrayList<>();
        List<BanResult> results = new CopyOnWriteArrayList<>();
        for (FeatureModule registeredModule : moduleManager.getModules()) {
            moduleExecutorFutures.add(CompletableFuture.runAsync(() -> {
                if(registeredModule.needCheckHandshake() && isHandshaking(peer)){
                   return; // 如果模块需要握手检查且peer正在握手 则跳过检查
                }
                BanResult banResult = registeredModule.shouldBanPeer(torrent, peer, ruleExecuteExecutor);
                results.add(banResult);
                synchronized (wakeLock) {
                    wakeLock.notifyAll();
                }
            }, checkBanExecutor));
        }
        while (moduleExecutorFutures.stream().anyMatch(future -> !future.isDone())) {
            synchronized (wakeLock) {
                try {
                    wakeLock.wait(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }
        }
        BanResult result = new BanResult(null, PeerAction.NO_ACTION, "No matches");
        for (BanResult r : results) {
            if (r.action() == PeerAction.SKIP) {
                result = r;
                break; // SKIP 是最高的不可覆盖优先级，提前退出循环
            }
            if (r.action() == PeerAction.BAN) {
                result = r;
                // 不要提前退出循环，BAN 结果可被后面到来的 SKIP 覆盖
            }
        }
        return result;
    }

    /**
     * 获取目前所有被封禁的对等体的集合的拷贝
     * @return 不可修改的集合拷贝
     */
    @NotNull
    public Map<PeerAddress, BanMetadata> getBannedPeers() {
        return ImmutableMap.copyOf(BAN_LIST);
    }

    /**
     * 以指定元数据封禁一个特定的对等体
     * @param peer 对等体 IP 地址
     * @param banMetadata 封禁元数据
     */
    public void banPeer(@NotNull PeerAddress peer, @NotNull BanMetadata banMetadata) {
        BAN_LIST.put(peer, banMetadata);
        metrics.recordPeerBan(peer, banMetadata);
        banListInvoker.forEach(i->i.add(peer,banMetadata));
        CompletableFuture.runAsync(()->{
            try {
               InetAddress address = InetAddress.getByName(peer.getAddress().toString());
               if(!address.getCanonicalHostName().equals(peer.getIp())){
                   banMetadata.setReverseLookup(address.getCanonicalHostName());
               }else{
                   banMetadata.setReverseLookup("N/A");
               }
            } catch (UnknownHostException ignored) {
                banMetadata.setReverseLookup("N/A");
            }
        }, generalExecutor);
    }

    /**
     * 解除一个指定对等体
     * @param address 对等体 IP 地址
     * @return 此对等体的封禁元数据；返回 null 代表此对等体没有被封禁
     */
    @Nullable
    public BanMetadata unbanPeer(@NotNull PeerAddress address) {
        BanMetadata metadata = BAN_LIST.remove(address);
        if (metadata != null) {
            metrics.recordPeerUnban(address, metadata);
            banListInvoker.forEach(i->i.add(address,metadata));
        }
        return metadata;
    }

    public record BanDownloaderResult(
            Downloader downloader, // 目标下载器
            boolean needUpdateBanList, // 是否需要向下载器更新 banlist
            Collection<Torrent> torrentsAffected // 受影响的 Torrent 列表
    ) {
    }

}
