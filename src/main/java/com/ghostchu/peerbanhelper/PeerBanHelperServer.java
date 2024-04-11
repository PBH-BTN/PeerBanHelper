package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.database.DatabaseHelper;
import com.ghostchu.peerbanhelper.database.DatabaseManager;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.downloader.DownloaderManager;
import com.ghostchu.peerbanhelper.metric.Metrics;
import com.ghostchu.peerbanhelper.metric.impl.persist.PersistMetrics;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.FeatureModule;
import com.ghostchu.peerbanhelper.module.ModuleManager;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.web.WebEndpointProvider;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class PeerBanHelperServer {

    private final Map<PeerAddress, BanMetadata> banList = new ConcurrentHashMap<>();

    private final Timer peerCheckTimer = new Timer("Peer check");
    private TimerTask banWaveTimerTask;

    @Getter
    private final DownloaderManager downloaderManager;

    private ExecutorService generalExecutor;
    private ExecutorService checkBanExecutor;
    private ExecutorService ruleExecuteExecutor;
    private ExecutorService downloaderApiExecutor;

    @Getter
    private Metrics metrics;
    private DatabaseManager databaseManager;
    @Getter
    private DatabaseHelper databaseHelper;

    private final ModuleManager moduleManager;
    
    private final YamlConfiguration profile;
    @Getter
    private final long banDuration;
    @Getter
    private final int httpdPort;
    @Getter
    private final boolean hideFinishLogs;
    @Getter
    private final YamlConfiguration mainConfig;

    @Getter
    private WebEndpointProvider webEndpointProviderServer;

    public PeerBanHelperServer(DownloaderManager downloaderManager, YamlConfiguration profile, YamlConfiguration mainConfig) throws SQLException {
        this.downloaderManager = downloaderManager;
        this.profile = profile;
        this.banDuration = profile.getLong("ban-duration");
        this.mainConfig = mainConfig;
        this.httpdPort = mainConfig.getInt("server.http");
        this.moduleManager = new ModuleManager(this, profile);
        this.hideFinishLogs = mainConfig.getBoolean("logger.hide-finish-log");
        loadExecutors();
        try {
            prepareDatabase();
        } catch (Exception e) {
            log.error(Lang.DATABASE_FAILURE, e);
            throw e;
        }
        registerMetrics();
        moduleManager.registerModules();
        registerTimer();
        registerBlacklistHttpServer();
    }

    public void shutdown() {
        this.metrics.close();
        moduleManager.unregisterModules();
        this.databaseManager.close();
        this.downloaderManager.stopAll();
        shutdownExecutors();
        this.webEndpointProviderServer.stop();
    }

    public void loadExecutors() {
        this.generalExecutor = Executors.newWorkStealingPool(mainConfig.getInt("threads.general-parallelism", 6));
        this.checkBanExecutor = Executors.newWorkStealingPool(mainConfig.getInt("threads.check-ban-parallelism", 8));
        this.ruleExecuteExecutor = Executors.newWorkStealingPool(mainConfig.getInt("threads.rule-execute-parallelism", 16));
        this.downloaderApiExecutor = Executors.newWorkStealingPool(mainConfig.getInt("threads.downloader-api-parallelism", 8));
    }

    public void shutdownExecutors() {
        this.checkBanExecutor.shutdown();
        this.ruleExecuteExecutor.shutdown();
        this.downloaderApiExecutor.shutdown();
        this.generalExecutor.shutdown();
    }

    private void prepareDatabase() throws SQLException {
        this.databaseManager = new DatabaseManager(this);
        this.databaseHelper = new DatabaseHelper(databaseManager);
    }

    private void registerMetrics() {
        this.metrics = new PersistMetrics(databaseHelper);
    }

    private void registerBlacklistHttpServer() {
        try {
            this.webEndpointProviderServer = new WebEndpointProvider(httpdPort, this);
        } catch (IOException e) {
            log.warn(Lang.ERR_INITIALIZE_BAN_PROVIDER_ENDPOINT_FAILURE, e);
        }
    }

    private void registerTimer() {
        if (banWaveTimerTask != null)
            banWaveTimerTask.cancel();

        banWaveTimerTask = new TimerTask() {
            @Override
            public void run() {
                banWave();
            }
        };

        peerCheckTimer.schedule(banWaveTimerTask, 0, profile.getLong("check-interval", 5000));
    }

    public void banWave() {
        List<Downloader> downloaders = downloaderManager.getDownloaders();
        try {
            downloaders.forEach(downloader -> downloader.setLastStatus(DownloaderLastStatus.HEALTHY));
            boolean needUpdate = false;
            Map<Downloader, Collection<Torrent>> needRelaunched = new HashMap<>();
            // 多线程处理下载器封禁操作
            for (Downloader downloader : downloaders) {
                try {
                    if (!downloader.login()) {
                        log.warn(Lang.ERR_CLIENT_LOGIN_FAILURE_SKIP, downloader.getName(), downloader.getEndpoint());
                        downloader.setLastStatus(DownloaderLastStatus.ERROR);
                        return;
                    }
                    Pair<Boolean, Collection<Torrent>> banDownloader = banDownloader(downloader);
                    if (banDownloader.getKey()) {
                        needUpdate = true;
                    }
                    needRelaunched.put(downloader, banDownloader.getValue());
                } catch (Throwable th) {
                    log.warn(Lang.ERR_UNEXPECTED_API_ERROR, downloader.getName(), downloader.getEndpoint(), th);
                    downloader.setLastStatus(DownloaderLastStatus.ERROR);
                }
            }

            List<PeerAddress> removeBan = new ArrayList<>();
            for (Map.Entry<PeerAddress, BanMetadata> pair : banList.entrySet()) {
                if (System.currentTimeMillis() >= pair.getValue().getUnbanAt()) {
                    removeBan.add(pair.getKey());
                }
            }

            removeBan.forEach(this::unbanPeer);
            if (!removeBan.isEmpty()) {
                log.info(Lang.PEER_UNBAN_WAVE, removeBan.size());
                needUpdate = true;
            }

            if (needUpdate) {
                for (Downloader downloader : downloaders) {
                    try {
                        if (!downloader.login()) {
                            log.warn(Lang.ERR_CLIENT_LOGIN_FAILURE_SKIP, downloader.getName(), downloader.getEndpoint());
                            downloader.setLastStatus(DownloaderLastStatus.ERROR);
                            return;
                        }
                        downloader.setBanList(banList.keySet());
                        downloader.relaunchTorrentIfNeeded(needRelaunched.getOrDefault(downloader, new ArrayList<>(0)));
                    } catch (Throwable th) {
                        log.warn(Lang.ERR_UPDATE_BAN_LIST, downloader.getName(), downloader.getEndpoint(), th);
                        downloader.setLastStatus(DownloaderLastStatus.ERROR);
                    }
                }
            }
        } finally {
            metrics.recordCheck();
            System.gc(); // Trigger serial GC on GraalVM NativeImage to avoid took too much memory, we build NativeImage because it took less memory than JVM and faster startup speed
        }
    }

    private Pair<Boolean, Collection<Torrent>> banDownloader(Downloader downloader) {
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
        return Pair.of(needUpdate.get(), needRelaunched);
    }

    private BanResult checkBan(Torrent torrent, Peer peer) {
        List<FeatureModule> modules = moduleManager.getRegisteredModules();
        Object wakeLock = new Object();
        List<CompletableFuture<Void>> moduleExecutorFutures = new ArrayList<>(modules.size());
        List<BanResult> results = new CopyOnWriteArrayList<>();
        for (FeatureModule registeredModule : modules) {
            moduleExecutorFutures.add(CompletableFuture.runAsync(() -> {
                BanResult banResult = registeredModule.shouldBanPeer(torrent, peer, ruleExecuteExecutor);
                results.add(banResult);
                synchronized (wakeLock) {
                    wakeLock.notify();
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
        for (BanResult result : results) {
            if (result.action() == PeerAction.SKIP || result.action() == PeerAction.BAN) {
                return result;
            }
        }
        return new BanResult(null, PeerAction.NO_ACTION, "No matches");
    }

    @NotNull
    public Map<PeerAddress, BanMetadata> getBannedPeers() {
        return ImmutableMap.copyOf(banList);
    }

    public void banPeer(@NotNull PeerAddress peer, @NotNull BanMetadata banMetadata) {
        banList.put(peer, banMetadata);
        metrics.recordPeerBan(peer, banMetadata);
    }


    public void unbanPeer(@NotNull PeerAddress address) {
        BanMetadata metadata = banList.remove(address);
        metrics.recordPeerUnban(address, metadata);
    }

}
