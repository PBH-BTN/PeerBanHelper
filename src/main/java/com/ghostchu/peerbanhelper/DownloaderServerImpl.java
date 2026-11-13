package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.banpipeline.DigestionSession;
import com.ghostchu.peerbanhelper.banpipeline.PeerCheckService;
import com.ghostchu.peerbanhelper.banpipeline.data.CheckResultBatch;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.peer.PeerImpl;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.bittorrent.torrent.TorrentImpl;
import com.ghostchu.peerbanhelper.databasent.service.BanListService;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderBasicInfo;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult;
import com.ghostchu.peerbanhelper.downloader.DownloaderManagerImpl;
import com.ghostchu.peerbanhelper.downloader.DownloaderRequestLock;
import com.ghostchu.peerbanhelper.event.banwave.LivePeersUpdatedEvent;
import com.ghostchu.peerbanhelper.event.banwave.PeerBanEvent;
import com.ghostchu.peerbanhelper.event.banwave.PeerUnbanEvent;
import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.ModuleManagerImpl;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.Pair;
import com.ghostchu.peerbanhelper.util.WatchDog;
import com.ghostchu.peerbanhelper.util.dns.DNSLookup;
import com.ghostchu.peerbanhelper.util.lab.Laboratory;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.wrapper.PeerMetadata;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.spotify.futures.CompletableFutures;
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

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public final class DownloaderServerImpl implements Reloadable, AutoCloseable, DownloaderServer {
    private static final long DEFAULT_PEER_POLLING_INTERVAL = 5_000L;
    private static final long DEFAULT_BANLIST_COMMIT_INTERVAL = 5_000L;
    private static final long DEFAULT_BANLIST_APPLY_GRACE_PERIOD = 60_000L;
    private static final long BANLIST_SAVE_INTERVAL = 60 * 60 * 1000L;

    @Getter
    private final DualIPv4v6Tries ignoreAddresses = new DualIPv4v6Tries();
    private final Lock peerPollingLock = new ReentrantLock();
    private final Lock banListCommitLock = new ReentrantLock();
    private final BanList banList;
    private final ConcurrentHashMap<IPAddress, PendingBanListOperation> pendingBanListOperations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<BanApplicationKey, BanApplicationState> banApplicationStates = new ConcurrentHashMap<>();
    private final DownloaderManagerImpl downloaderManager;
    private final BasicMetrics metrics;
    private final ModuleManagerImpl moduleManager;
    private final BanListService banListDao;
    private final DNSLookup dnsLookup;
    private final Laboratory laboratory;
    private final AlertManager alertManager;
    private final PeerCheckService peerCheckService;
    private final DownloaderRequestLock downloaderRequestLock = new DownloaderRequestLock();
    private final ExecutorService mainWorkStealingService = Executors.newWorkStealingPool();
    private final ExecutorService scheduleEnergy = Executors.newWorkStealingPool(Math.clamp(
            Runtime.getRuntime().availableProcessors(),
            ExternalSwitch.parseInt("pbh.digestSession.scheduleEnergy.min", 2),
            ExternalSwitch.parseInt("pbh.digestSession.scheduleEnergy.max", 4)
    ));
    private final ExecutorService digestEnergyCompute = Executors.newWorkStealingPool(Math.clamp(
            Runtime.getRuntime().availableProcessors() / 2,
            ExternalSwitch.parseInt("pbh.digestSession.digestEnergyCompute.min", 2),
            ExternalSwitch.parseInt("pbh.digestSession.digestEnergyCompute.max", 8)
    ));
    private final ExecutorService digestEnergyIO = Executors.newVirtualThreadPerTaskExecutor();
    @Getter
    private volatile long banDuration;
    private volatile Map<PeerAddress, List<PeerMetadata>> livePeers = Map.of();
    @Getter
    private volatile boolean hideFinishLogs;
    @Getter
    private final AtomicBoolean needReApplyBanList = new AtomicBoolean();
    private volatile ScheduledExecutorService peerPollingService;
    private volatile ScheduledExecutorService banListService;
    private volatile WatchDog peerPollingWatchDog;
    private volatile WatchDog banListWatchDog;
    private volatile ScheduledFuture<?> saveBanListFuture;
    @Getter
    private volatile boolean globalPaused = false;

    public DownloaderServerImpl(BanList banList,
                                DownloaderManagerImpl downloaderManager,
                                @Qualifier("persistMetrics") BasicMetrics metrics,
                                ModuleManagerImpl moduleManager,
                                BanListService banListDao,
                                DNSLookup dnsLookup,
                                Laboratory laboratory,
                                AlertManager alertManager) {
        this.banList = banList;
        this.downloaderManager = downloaderManager;
        this.metrics = metrics;
        this.banListDao = banListDao;
        this.dnsLookup = dnsLookup;
        this.moduleManager = moduleManager;
        this.laboratory = laboratory;
        this.alertManager = alertManager;
        this.peerCheckService = new PeerCheckService(moduleManager, alertManager, this, digestEnergyCompute);
        Main.getReloadManager().register(this);
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
        needReApplyBanList.set(true);
        unbanWhitelistedPeers();
        registerTimer();
    }

    @Override
    public void close() {
        if (saveBanListFuture != null) {
            saveBanListFuture.cancel(false);
        }
        shutdown(peerPollingService);
        shutdown(banListService);
        if (peerPollingWatchDog != null) {
            peerPollingWatchDog.close();
        }
        if (banListWatchDog != null) {
            banListWatchDog.close();
        }
        mainWorkStealingService.shutdown();
        scheduleEnergy.shutdown();
        digestEnergyCompute.shutdown();
        digestEnergyIO.shutdown();
        this.metrics.close();
        saveBanList();
    }

    private void shutdown(@Nullable ExecutorService executor) {
        if (executor != null) {
            executor.shutdown();
        }
    }

    private void unbanWhitelistedPeers() {
        List<IPAddress> addresses = new ArrayList<>();
        banList.forEach((address, metadata) -> {
            if (ignoreAddresses.elementsContaining(address) != null) {
                addresses.add(address);
            }
        });
        addresses.forEach(this::enqueueUnban);
    }

    public void loadBanListToMemory() {
        if (!Main.getMainConfig().getBoolean("persist.banlist")) {
            return;
        }
        this.banList.reset();
        this.pendingBanListOperations.clear();
        this.banApplicationStates.clear();
        try {
            Map<IPAddress, BanMetadata> data = banListDao.readBanList();
            this.banList.addAll(data);
            log.info(tlUI(Lang.LOAD_BANLIST_FROM_FILE, data.size()));
        } catch (Exception exception) {
            log.error(tlUI(Lang.ERR_UPDATE_BAN_LIST), exception);
        }
    }

    private void saveBanList() {
        if (!Main.getMainConfig().getBoolean("persist.banlist")) {
            return;
        }
        try {
            int count = banListDao.saveBanList(banList);
            log.info(tlUI(Lang.SAVED_BANLIST, count));
        } catch (Exception exception) {
            log.error(tlUI(Lang.SAVE_BANLIST_FAILED), exception);
        }
    }

    /**
     * Polls downloaders, checks peers and queues ban-list changes.
     * This task never mutates or applies the ban list directly.
     */
    public void runPeerPollingCycle() {
        boolean locked = false;
        try {
            if (!peerPollingLock.tryLock(3, TimeUnit.SECONDS)) {
                return;
            }
            locked = true;
            if (isGlobalPaused()) {
                return;
            }

            peerPollingWatchDog.setLastOperation("Peer polling - start", false);
            long startAt = System.currentTimeMillis();
            peerPollingWatchDog.setLastOperation("Run downloader scheduled tasks", true);
            downloaderManager.forEach(Downloader::runScheduleTasks);

            Pair<Map<Downloader, List<BanDetail>>, DigestionSession.ProcessingStatistics> sessionResult;
            try (DigestionSession session = new DigestionSession(
                    downloaderManager,
                    this,
                    moduleManager,
                    peerCheckService,
                    downloaderRequestLock,
                    scheduleEnergy,
                    digestEnergyCompute,
                    digestEnergyIO
            )) {
                peerPollingWatchDog.setLastOperation("Running peer polling pipeline", true);
                sessionResult = session.runPollingCycle(peerPollingWatchDog);
            }

            int queuedBans = 0;
            for (Map.Entry<Downloader, List<BanDetail>> entry : sessionResult.getKey().entrySet()) {
                for (BanDetail detail : entry.getValue()) {
                    PeerAction action = detail.result().action();
                    if (action != PeerAction.BAN && action != PeerAction.BAN_FOR_DISCONNECT) {
                        continue;
                    }
                    if (enqueuePeerBan(entry.getKey(), detail)) {
                        queuedBans++;
                    }
                }
            }

            DigestionSession.ProcessingStatistics statistics = sessionResult.getValue();
            if (!hideFinishLogs && !downloaderManager.isEmpty()) {
                log.info(tlUI(
                        Lang.PEER_POLLING_COMPLETED,
                        statistics.downloaders(),
                        statistics.torrents(),
                        statistics.peers(),
                        queuedBans,
                        System.currentTimeMillis() - startAt
                ));
            }
            peerPollingWatchDog.setLastOperation("Completed", false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Throwable throwable) {
            log.error(tlUI(Lang.UNABLE_COMPLETE_SCHEDULE_TASKS), throwable);
        } finally {
            if (locked) {
                peerPollingWatchDog.feed();
                metrics.recordCheck();
                peerPollingLock.unlock();
            }
        }
    }

    /**
     * Applies queued changes and submits the resulting ban list to downloaders.
     */
    public void commitBanList() {
        boolean locked = false;
        try {
            if (!banListCommitLock.tryLock(3, TimeUnit.SECONDS)) {
                return;
            }
            locked = true;
            long startAt = System.currentTimeMillis();
            banListWatchDog.setLastOperation("Ban list commit - start", false);

            boolean applyFullList = needReApplyBanList.getAndSet(false);
            List<BanMetadata> removedPeers = removeExpiredBans();
            List<BanMetadata> addedPeers = new ArrayList<>();
            int scheduledOperations = drainPendingBanListOperations(addedPeers, removedPeers);
            if (scheduledOperations > 0) {
                log.info(tlUI(Lang.SCHEDULED_OPERATIONS, scheduledOperations));
            }

            boolean hasChanges = !addedPeers.isEmpty() || !removedPeers.isEmpty();
            int updatedDownloaders = 0;
            if (applyFullList || hasChanges) {
                if (applyFullList) {
                    log.info(tlUI(Lang.APPLYING_FULL_BANLIST_TO_DOWNLOADER));
                }
                List<Downloader> downloaders = List.copyOf(downloaderManager);
                List<CompletableFuture<Map.Entry<String, Boolean>>> updateFutures = downloaders.stream()
                        .map(downloader -> CompletableFuture.supplyAsync(
                                () -> Map.entry(
                                        downloader.getId(),
                                        updateDownloader(
                                                downloader,
                                                applyFullList,
                                                addedPeers,
                                                removedPeers
                                        )
                                ),
                                mainWorkStealingService
                        ))
                        .toList();
                Set<String> successfulDownloaderIds = new HashSet<>();
                for (CompletableFuture<Map.Entry<String, Boolean>> future : updateFutures) {
                    Map.Entry<String, Boolean> result = future.join();
                    if (result.getValue()) {
                        successfulDownloaderIds.add(result.getKey());
                        updatedDownloaders++;
                    }
                }
                recordBanApplications(addedPeers, successfulDownloaderIds);
                if ((applyFullList || hasChanges) && !downloaders.isEmpty() && successfulDownloaderIds.isEmpty()) {
                    needReApplyBanList.set(true);
                }
            }

            pruneBanApplicationStates();
            if (updatedDownloaders > 0) {
                log.info(tlUI(
                        Lang.BANLIST_COMMIT_COMPLETED,
                        updatedDownloaders,
                        addedPeers.size(),
                        removedPeers.size(),
                        System.currentTimeMillis() - startAt
                ));
            }
            banListWatchDog.setLastOperation("Completed", false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Throwable throwable) {
            log.error(tlUI(Lang.UNABLE_COMPLETE_SCHEDULE_TASKS), throwable);
        } finally {
            if (locked) {
                banListWatchDog.feed();
                banListCommitLock.unlock();
            }
        }
    }

    private int drainPendingBanListOperations(@NotNull List<BanMetadata> addedPeers,
                                              @NotNull List<BanMetadata> removedPeers) {
        int processed = 0;
        for (Map.Entry<IPAddress, PendingBanListOperation> entry : pendingBanListOperations.entrySet()) {
            PendingBanListOperation operation = entry.getValue();
            if (!pendingBanListOperations.remove(entry.getKey(), operation)) {
                continue;
            }
            processed++;
            try {
                if (operation instanceof PendingUnban pendingUnban) {
                    removedPeers.addAll(unbanPeers(List.of(pendingUnban.address())));
                } else {
                    commitBanOperation(operation, addedPeers);
                }
            } catch (Throwable throwable) {
                log.error(tlUI(Lang.UNABLE_COMPLETE_PEER_BAN_TASK), throwable);
            }
        }
        return processed;
    }

    private void commitBanOperation(@NotNull PendingBanListOperation operation,
                                    @NotNull List<BanMetadata> addedPeers) {
        IPAddress address = operation.address();
        if (banList.contains(address)) {
            observeAlreadyBanned(operation.downloader(), address);
            return;
        }

        PreparedBan preparedBan = prepareBan(operation);
        BanMetadata previous = banList.add(preparedBan.peer().getPeerAddress(), preparedBan.metadata());
        if (previous != null) {
            observeAlreadyBanned(operation.downloader(), address);
            return;
        }

        addedPeers.add(preparedBan.metadata());
        metrics.recordPeerBan(address, preparedBan.metadata());
        preparedBan.metadata().setReverseLookup("N/A");
        if (Main.getMainConfig().getBoolean("lookup.dns-reverse-lookup")) {
            dnsLookup.ptr(address.toReverseDNSLookupString()).thenAccept(hostName ->
                    hostName.ifPresent(name -> {
                        if (!preparedBan.peer().getPeerAddress().getIp().equals(name)) {
                            preparedBan.metadata().setReverseLookup(name);
                        }
                    })
            );
        }
        Main.getEventBus().post(new PeerBanEvent(
                preparedBan.peer().getPeerAddress(),
                preparedBan.metadata(),
                preparedBan.torrent(),
                preparedBan.peer()
        ));
        if (preparedBan.logBan()) {
            log.info(tlUI(
                    Lang.BAN_PEER,
                    preparedBan.peer().getPeerAddress(),
                    preparedBan.peer().getPeerId(),
                    preparedBan.peer().getClientName(),
                    preparedBan.peer().getProgress(),
                    preparedBan.peer().getUploaded(),
                    preparedBan.peer().getDownloaded(),
                    preparedBan.torrent().getName(),
                    tlUI(preparedBan.metadata().getDescription())
            ));
        }
    }

    private @NotNull PreparedBan prepareBan(@NotNull PendingBanListOperation operation) {
        if (operation instanceof PendingMetadataBan pendingMetadataBan) {
            return new PreparedBan(
                    pendingMetadataBan.downloader(),
                    pendingMetadataBan.torrent(),
                    pendingMetadataBan.peer(),
                    pendingMetadataBan.metadata(),
                    false
            );
        }
        if (operation instanceof PendingIpBan pendingIpBan) {
            PeerAddress peerAddress = new PeerAddress(
                    pendingIpBan.address().toCompressedString(),
                    0,
                    pendingIpBan.address().toCompressedString()
            );
            String mockTorrentHash = "00000000000000000000";
            Torrent torrent = new TorrentImpl(
                    mockTorrentHash,
                    "User Operation",
                    mockTorrentHash,
                    0,
                    0,
                    0.0d,
                    0,
                    0,
                    false
            );
            Peer peer = new PeerImpl(
                    peerAddress,
                    "-USROPS-".getBytes(StandardCharsets.ISO_8859_1),
                    "User Operation",
                    0,
                    0,
                    0,
                    0,
                    0,
                    null,
                    false
            );
            Downloader downloader = downloaderManager.isEmpty() ? null : downloaderManager.getFirst();
            long permanentBanDuration = 100L * 365 * 24 * 60 * 60 * 1000;
            BanMetadata metadata = new BanMetadata(
                    getClass().getName(),
                    UUID.randomUUID().toString().replace("-", ""),
                    downloaderInfo(downloader),
                    OffsetDateTime.now(),
                    OffsetDateTime.now().plus(permanentBanDuration, ChronoUnit.MILLIS),
                    false,
                    false,
                    false,
                    torrent,
                    peer,
                    new TranslationComponent(Lang.PEER_BAN_USER_OPERATE_TITLE),
                    new TranslationComponent(Lang.PEER_BAN_USER_OPERATE_DESCRIPTION, peerAddress),
                    StructuredData.create().add("type", "manually").add("ip", peerAddress)
            );
            return new PreparedBan(downloader, torrent, peer, metadata, false);
        }

        PendingPeerBan pendingPeerBan = (PendingPeerBan) operation;
        BanDetail detail = pendingPeerBan.detail();
        long actualBanDuration = detail.banDuration() > 0 ? detail.banDuration() : banDuration;
        BanMetadata metadata = new BanMetadata(
                detail.result().moduleContext().getName(),
                UUID.randomUUID().toString().replace("-", ""),
                downloaderInfo(pendingPeerBan.downloader()),
                OffsetDateTime.now(),
                OffsetDateTime.now().plus(actualBanDuration, ChronoUnit.MILLIS),
                detail.result().action() == PeerAction.BAN_FOR_DISCONNECT,
                detail.result().action().isExcludeFromReport(),
                detail.result().action().isExcludeFromDisplay(),
                detail.torrent(),
                detail.peer(),
                detail.result().rule(),
                detail.result().reason(),
                detail.result().structuredData()
        );
        return new PreparedBan(
                pendingPeerBan.downloader(),
                detail.torrent(),
                detail.peer(),
                metadata,
                detail.result().action() != PeerAction.BAN_FOR_DISCONNECT
        );
    }

    private @NotNull DownloaderBasicInfo downloaderInfo(@Nullable Downloader downloader) {
        if (downloader == null) {
            return new DownloaderBasicInfo("manual", "User Operation", "manual");
        }
        return downloaderManager.getDownloadInfo(downloader);
    }

    private boolean enqueuePeerBan(@NotNull Downloader downloader, @NotNull BanDetail detail) {
        return enqueueBan(
                detail.peer().getPeerAddress().getAddress().toPrefixBlock(),
                downloader,
                () -> new PendingPeerBan(
                        detail.peer().getPeerAddress().getAddress().toPrefixBlock(),
                        downloader,
                        detail,
                        System.currentTimeMillis()
                )
        );
    }

    private boolean enqueueMetadataBan(@Nullable Downloader downloader,
                                       @NotNull BanMetadata metadata,
                                       @NotNull Torrent torrent,
                                       @NotNull Peer peer) {
        return enqueueBan(
                peer.getPeerAddress().getAddress().toPrefixBlock(),
                downloader,
                () -> new PendingMetadataBan(
                        peer.getPeerAddress().getAddress().toPrefixBlock(),
                        downloader,
                        torrent,
                        peer,
                        metadata,
                        System.currentTimeMillis()
                )
        );
    }

    private boolean enqueueIpBan(@NotNull PeerAddress address) {
        IPAddress normalized = address.getAddress().toPrefixBlock();
        return enqueueBan(
                normalized,
                null,
                () -> new PendingIpBan(normalized, System.currentTimeMillis())
        );
    }

    private boolean enqueueBan(@NotNull IPAddress address,
                               @Nullable Downloader downloader,
                               @NotNull java.util.function.Supplier<PendingBanListOperation> operationFactory) {
        if (banList.contains(address)) {
            observeAlreadyBanned(downloader, address);
            return false;
        }
        AtomicBoolean enqueued = new AtomicBoolean();
        pendingBanListOperations.compute(address, (key, existing) -> {
            if (existing instanceof PendingBanListOperation && !(existing instanceof PendingUnban)) {
                return existing;
            }
            enqueued.set(true);
            return operationFactory.get();
        });
        return enqueued.get();
    }

    private void enqueueUnban(@NotNull IPAddress address) {
        IPAddress normalized = address.toPrefixBlock();
        pendingBanListOperations.compute(normalized, (key, existing) -> {
            if (existing instanceof PendingUnban) {
                return existing;
            }
            return new PendingUnban(normalized, System.currentTimeMillis());
        });
    }

    private void observeAlreadyBanned(@Nullable Downloader downloader, @NotNull IPAddress address) {
        BanMetadata metadata = banList.get(address);
        if (metadata == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long gracePeriod = Math.max(0, Main.getProfileConfig().getLong(
                "banlist-apply-grace-period",
                DEFAULT_BANLIST_APPLY_GRACE_PERIOD
        ));
        BanApplicationKey key = new BanApplicationKey(
                downloader == null ? "*" : downloader.getId(),
                address
        );
        AtomicReference<Boolean> warn = new AtomicReference<>(false);
        banApplicationStates.compute(key, (ignored, state) -> {
            BanApplicationState current = state;
            if (current == null) {
                current = new BanApplicationState(metadata.getRandomId(), now);
            }
            warn.set(current.observe(metadata.getRandomId(), now, gracePeriod));
            return current;
        });
        if (warn.get()) {
            log.warn(tlUI(Lang.DUPLICATE_BAN, metadata));
            log.warn(tlUI(Lang.SCHEDULED_FULL_BANLIST_APPLY));
            needReApplyBanList.set(true);
        } else {
            log.debug("Ignoring duplicate ban for {} during ban-list apply grace period", address);
        }
    }

    private void recordBanApplications(@NotNull Collection<BanMetadata> addedPeers,
                                       @NotNull Set<String> successfulDownloaderIds) {
        if (addedPeers.isEmpty() || successfulDownloaderIds.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (BanMetadata metadata : addedPeers) {
            IPAddress address = metadata.getPeer().getAddress().getAddress().toPrefixBlock();
            for (String downloaderId : successfulDownloaderIds) {
                banApplicationStates.compute(
                        new BanApplicationKey(downloaderId, address),
                        (ignored, state) -> {
                            BanApplicationState current = state;
                            if (current == null) {
                                current = new BanApplicationState(metadata.getRandomId(), now);
                            }
                            current.markApplied(metadata.getRandomId(), now);
                            return current;
                        }
                );
            }
        }
    }

    private void pruneBanApplicationStates() {
        banApplicationStates.entrySet().removeIf(entry -> {
            BanMetadata metadata = banList.get(entry.getKey().address());
            return metadata == null || !Objects.equals(metadata.getRandomId(), entry.getValue().banId());
        });
    }

    private void removeBanApplicationStates(@NotNull IPAddress address) {
        banApplicationStates.keySet().removeIf(key ->
                key.address().equals(address)
                        || address.contains(key.address())
                        || key.address().contains(address)
        );
    }

    public void updateLivePeers(@NotNull Map<Downloader, Map<Torrent, List<Peer>>> peers) {
        Map<PeerAddress, List<PeerMetadata>> liveSnapshot = new HashMap<>();
        peers.forEach((downloader, tasks) ->
                tasks.forEach((torrent, peerList) ->
                        peerList.forEach(peer -> {
                            PeerAddress address = peer.getPeerAddress();
                            List<PeerMetadata> metadata = liveSnapshot.computeIfAbsent(address, ignored -> new ArrayList<>());
                            metadata.add(new PeerMetadata(downloaderManager.getDownloadInfo(downloader), torrent, peer));
                        })
                )
        );
        Map<PeerAddress, List<PeerMetadata>> immutableSnapshot = liveSnapshot.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())
                ));
        livePeers = immutableSnapshot;
        Main.getEventBus().post(new LivePeersUpdatedEvent(immutableSnapshot));
    }

    /**
     * Applies the current in-memory ban list to one downloader.
     *
     * @return whether the update completed successfully
     */
    private boolean updateDownloader(@NotNull Downloader downloader,
                                     boolean applyFullList,
                                     @NotNull Collection<BanMetadata> added,
                                     @NotNull Collection<BanMetadata> removed) {
        return downloaderRequestLock.withLock(downloader, () -> {
            try {
                DownloaderLoginResult loginResult = downloader.login();
                if (!loginResult.success()) {
                    if (loginResult.status() != DownloaderLoginResult.Status.PAUSED) {
                        log.error(tlUI(
                                Lang.ERR_CLIENT_LOGIN_FAILURE_SKIP,
                                downloader.getName(),
                                downloader.getEndpoint(),
                                tlUI(loginResult.message())
                        ));
                        downloader.setLastStatus(DownloaderLastStatus.ERROR, loginResult.message());
                    }
                    return false;
                }
                downloader.setLastStatus(DownloaderLastStatus.HEALTHY, loginResult.message());
                downloader.setBanList(banList.copyKeySet(), added, removed, applyFullList);
                return true;
            } catch (Throwable throwable) {
                log.error(tlUI(Lang.ERR_UPDATE_BAN_LIST, downloader.getName(), downloader.getEndpoint()), throwable);
                downloader.setLastStatus(
                        DownloaderLastStatus.ERROR,
                        new TranslationComponent(
                                Lang.STATUS_TEXT_EXCEPTION,
                                throwable.getClass().getName() + ": " + throwable.getMessage()
                        )
                );
                return false;
            }
        });
    }

    /**
     * Removes expired bans. This method is only called by the ban-list commit task.
     */
    public @NotNull List<BanMetadata> removeExpiredBans() {
        List<IPAddress> expiredAddresses = new ArrayList<>();
        banList.forEach((address, metadata) -> {
            if (OffsetDateTime.now().isAfter(metadata.getUnbanAt())) {
                expiredAddresses.add(address);
            }
        });
        List<BanMetadata> removed = unbanPeers(expiredAddresses);
        long normalUnbanCount = removed.stream().filter(metadata -> !metadata.isBanForDisconnect()).count();
        if (normalUnbanCount > 0) {
            log.info(tlUI(Lang.PEER_UNBAN_WAVE, normalUnbanCount));
        }
        return removed;
    }

    @Override
    public @NotNull Map<PeerAddress, List<PeerMetadata>> getPeerSnapshot() {
        return livePeers;
    }

    @Override
    public void scheduleBanPeerNoAssign(@NotNull BanMetadata banMetadata,
                                        @NotNull Torrent torrent,
                                        @NotNull Peer peer) {
        Downloader downloader = downloaderManager.stream()
                .filter(candidate -> candidate.getId().equals(banMetadata.getDownloader().id()))
                .findFirst()
                .orElse(null);
        enqueueMetadataBan(downloader, banMetadata, torrent, peer);
    }

    @Override
    public void scheduleBanPeerNoAssign(@NotNull PeerAddress address) {
        enqueueIpBan(address);
    }

    @Override
    public void scheduleBanPeerNoAssign(@NotNull CheckResultBatch checkResult) {
        PeerAction action = checkResult.checkResult().action();
        if (action != PeerAction.BAN && action != PeerAction.BAN_FOR_DISCONNECT) {
            return;
        }
        enqueuePeerBan(
                checkResult.downloader(),
                new BanDetail(
                        checkResult.torrent(),
                        checkResult.peer(),
                        checkResult.checkResult(),
                        checkResult.checkResult().duration()
                )
        );
    }

    @Override
    public void scheduleUnBanPeer(@NotNull PeerAddress peer) {
        enqueueUnban(peer.getAddress());
    }

    @Override
    public void scheduleUnBanPeer(@NotNull IPAddress peer) {
        enqueueUnban(peer);
    }

    /**
     * Removes peers from memory and returns their metadata.
     */
    private @NotNull List<BanMetadata> unbanPeers(@NotNull Collection<IPAddress> addresses) {
        List<AssociativeAddressTrie.@Nullable AssociativeTrieNode<? extends IPAddress, BanMetadata>> unbanned = new ArrayList<>();
        for (IPAddress address : addresses) {
            AssociativeAddressTrie.@Nullable AssociativeTrieNode<? extends IPAddress, BanMetadata> node = banList.remove(address);
            if (node != null) {
                unbanned.add(node);
                removeBanApplicationStates(address);
            }
        }
        Map<IPAddress, BanMetadata> metadata = unbanned.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        AssociativeAddressTrie.AssociativeTrieNode::getKey,
                        AssociativeAddressTrie.AssociativeTrieNode::getValue
                ));
        if (!metadata.isEmpty()) {
            Main.getEventBus().post(new PeerUnbanEvent(metadata));
        }
        for (var node : unbanned) {
            metrics.recordPeerUnban(node.getKey(), node.getValue());
        }
        return metadata.values().stream().toList();
    }

    @Override
    public Map<PeerAddress, List<PeerMetadata>> getLivePeersSnapshot() {
        return livePeers;
    }

    private void registerTimer() {
        if (saveBanListFuture != null) {
            saveBanListFuture.cancel(false);
        }
        saveBanListFuture = CommonUtil.getScheduler().scheduleWithFixedDelay(
                this::saveBanList,
                10 * 1000,
                BANLIST_SAVE_INTERVAL,
                TimeUnit.MILLISECONDS
        );

        long peerPollingInterval = Math.max(
                1_000,
                Main.getProfileConfig().getLong("check-interval", DEFAULT_PEER_POLLING_INTERVAL)
        );
        long banListCommitInterval = Math.max(
                1_000,
                Main.getProfileConfig().getLong("banlist-commit-interval", DEFAULT_BANLIST_COMMIT_INTERVAL)
        );
        if (peerPollingWatchDog != null) {
            peerPollingWatchDog.close();
        }
        if (banListWatchDog != null) {
            banListWatchDog.close();
        }
        peerPollingWatchDog = new WatchDog(
                "Peer Polling Thread",
                peerPollingInterval + 60_000,
                this::peerPollingWatchDogHungry,
                null
        );
        banListWatchDog = new WatchDog(
                "BanList Commit Thread",
                banListCommitInterval + 120_000,
                this::banListWatchDogHungry,
                null
        );
        registerPeerPollingTimer(peerPollingInterval);
        registerBanListTimer(banListCommitInterval);
        peerPollingWatchDog.start();
        banListWatchDog.start();
    }

    private void registerPeerPollingTimer(long interval) {
        shutdown(peerPollingService);
        peerPollingService = Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("Peer Polling");
            thread.setDaemon(true);
            return thread;
        });
        peerPollingService.scheduleWithFixedDelay(this::runPeerPollingCycle, 1, interval, TimeUnit.MILLISECONDS);
    }

    private void registerBanListTimer(long interval) {
        shutdown(banListService);
        banListService = Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("BanList Commit");
            thread.setDaemon(true);
            return thread;
        });
        banListService.scheduleWithFixedDelay(this::commitBanList, 1, interval, TimeUnit.MILLISECONDS);
    }

    @Override
    public @NotNull BanList getBanList() {
        return banList;
    }

    @Override
    public @NotNull PeerCheckService getPeerCheckService() {
        return peerCheckService;
    }

    private void peerPollingWatchDogHungry() {
        log.error(MiscUtil.getAllThreadTrace());
        long interval = Math.max(
                1_000,
                Main.getProfileConfig().getLong("check-interval", DEFAULT_PEER_POLLING_INTERVAL)
        );
        registerPeerPollingTimer(interval);
        Main.getGuiManager().createNotification(
                Level.WARN,
                tlUI(Lang.BAN_WAVE_WATCH_DOG_TITLE),
                tlUI(Lang.BAN_WAVE_WATCH_DOG_DESCRIPTION)
        );
    }

    private void banListWatchDogHungry() {
        log.error(MiscUtil.getAllThreadTrace());
        long interval = Math.max(
                1_000,
                Main.getProfileConfig().getLong("banlist-commit-interval", DEFAULT_BANLIST_COMMIT_INTERVAL)
        );
        registerBanListTimer(interval);
        Main.getGuiManager().createNotification(
                Level.WARN,
                tlUI(Lang.BAN_WAVE_WATCH_DOG_TITLE),
                tlUI(Lang.BAN_WAVE_WATCH_DOG_DESCRIPTION)
        );
    }

    @Override
    public void setGlobalPaused(boolean globalPaused) {
        this.globalPaused = globalPaused;
        if (globalPaused) {
            ExchangeMap.GUI_DISPLAY_FLAGS.add(new ExchangeMap.DisplayFlag(
                    "global-paused",
                    20,
                    tlUI(Lang.STATUS_BAR_GLOBAL_PAUSED)
            ));
        } else {
            ExchangeMap.GUI_DISPLAY_FLAGS.removeIf(flag -> "global-paused".equals(flag.getId()));
        }
    }

    public record BanDetail(
            Torrent torrent,
            Peer peer,
            CheckResult result,
            long banDuration
    ) {
    }

    private interface PendingBanListOperation {
        @NotNull
        IPAddress address();

        long enqueuedAt();

        @Nullable
        Downloader downloader();
    }

    private record PendingPeerBan(
            @NotNull IPAddress address,
            @NotNull Downloader downloader,
            @NotNull BanDetail detail,
            long enqueuedAt
    ) implements PendingBanListOperation {
    }

    private record PendingMetadataBan(
            @NotNull IPAddress address,
            @Nullable Downloader downloader,
            @NotNull Torrent torrent,
            @NotNull Peer peer,
            @NotNull BanMetadata metadata,
            long enqueuedAt
    ) implements PendingBanListOperation {
    }

    private record PendingIpBan(
            @NotNull IPAddress address,
            long enqueuedAt
    ) implements PendingBanListOperation {
        @Override
        public @Nullable Downloader downloader() {
            return null;
        }
    }

    private record PendingUnban(
            @NotNull IPAddress address,
            long enqueuedAt
    ) implements PendingBanListOperation {
        @Override
        public @Nullable Downloader downloader() {
            return null;
        }
    }

    private record PreparedBan(
            @Nullable Downloader downloader,
            @NotNull Torrent torrent,
            @NotNull Peer peer,
            @NotNull BanMetadata metadata,
            boolean logBan
    ) {
    }

    private record BanApplicationKey(
            @NotNull String downloaderId,
            @NotNull IPAddress address
    ) {
    }

    private static final class BanApplicationState {
        private String banId;
        private long lastAppliedAt;
        private boolean warned;

        private BanApplicationState(@NotNull String banId, long observedAt) {
            this.banId = banId;
            this.lastAppliedAt = observedAt;
        }

        private synchronized boolean observe(@NotNull String banId, long observedAt, long gracePeriod) {
            if (!Objects.equals(this.banId, banId)) {
                this.banId = banId;
                this.lastAppliedAt = observedAt;
                this.warned = false;
                return false;
            }
            if (observedAt - lastAppliedAt < gracePeriod) {
                return false;
            }
            if (warned) {
                return false;
            }
            warned = true;
            return true;
        }

        private synchronized void markApplied(@NotNull String banId, long appliedAt) {
            if (!Objects.equals(this.banId, banId)) {
                this.banId = banId;
                this.warned = false;
            }
            this.lastAppliedAt = appliedAt;
        }

        private synchronized String banId() {
            return banId;
        }
    }
}
