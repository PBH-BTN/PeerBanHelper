package com.ghostchu.peerbanhelper.banpipeline;

import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.banpipeline.data.CheckResultBatch;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.ModuleManager;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.module.PeerCheckContext;
import com.ghostchu.peerbanhelper.module.RuleFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

/**
 * Shared peer rule-check entry point for polling and event-driven callers.
 */
@Slf4j
public final class PeerCheckService {
    private static final CheckResult NO_ACTION_RESULT = new CheckResult(
            PeerCheckService.class,
            PeerAction.NO_ACTION,
            0,
            new TranslationComponent("N/A"),
            new TranslationComponent("Check passed"),
            StructuredData.create().add("status", "pass")
    );

    private final ConcurrentHashMap<PeerCheckKey, CompletableFuture<CheckResultBatch>> inFlightChecks = new ConcurrentHashMap<>();
    private final ModuleManager moduleManager;
    private final AlertManager alertManager;
    private final DownloaderServer downloaderServer;
    private final ExecutorService executor;

    public PeerCheckService(ModuleManager moduleManager,
                            AlertManager alertManager,
                            DownloaderServer downloaderServer,
                            ExecutorService executor) {
        this.moduleManager = moduleManager;
        this.alertManager = alertManager;
        this.downloaderServer = downloaderServer;
        this.executor = executor;
    }

    /**
     * Checks a peer with all enabled rule modules.
     * Concurrent checks for the same downloader, torrent and peer share one result.
     */
    public @NotNull CompletableFuture<CheckResultBatch> checkPeerAsync(@NotNull Downloader downloader,
                                                                       @NotNull Torrent torrent,
                                                                       @NotNull Peer peer,
                                                                       @NotNull PeerCheckContext context) {
        PeerCheckKey key = new PeerCheckKey(
                downloader.getId(),
                torrent.getHash(),
                peer.getPeerAddress().getAddress().toPrefixBlock().toCompressedString(),
                peer.getPeerAddress().getPort()
        );
        CompletableFuture<CheckResultBatch> future = new CompletableFuture<>();
        CompletableFuture<CheckResultBatch> existing = inFlightChecks.putIfAbsent(key, future);
        if (existing != null) {
            return existing;
        }

        CheckResult badConfigResult = checkIfPossibleBadConfig(downloader, torrent, peer);
        if (badConfigResult != null) {
            future.complete(new CheckResultBatch(downloader, torrent, peer, badConfigResult));
            inFlightChecks.remove(key, future);
            return future;
        }

        List<RuleFeatureModule> ruleModules = moduleManager.getModules().stream()
                .filter(RuleFeatureModule.class::isInstance)
                .map(RuleFeatureModule.class::cast)
                .toList();
        if (ruleModules.isEmpty()) {
            future.complete(new CheckResultBatch(downloader, torrent, peer, NO_ACTION_RESULT));
            inFlightChecks.remove(key, future);
            return future;
        }

        List<CompletableFuture<CheckResult>> moduleResults = new ArrayList<>(ruleModules.size());
        for (RuleFeatureModule module : ruleModules) {
            moduleResults.add(CompletableFuture.supplyAsync(
                    () -> checkWithModule(module, downloader, torrent, peer, context),
                    executor
            ));
        }

        CompletableFuture.allOf(moduleResults.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, throwable) -> {
                    try {
                        if (throwable != null) {
                            future.completeExceptionally(throwable);
                            return;
                        }
                        CheckResult merged = mergeResults(moduleResults);
                        future.complete(new CheckResultBatch(downloader, torrent, peer, merged));
                    } finally {
                        inFlightChecks.remove(key, future);
                    }
                });
        return future;
    }

    private @NotNull CheckResult checkWithModule(@NotNull RuleFeatureModule module,
                                                 @NotNull Downloader downloader,
                                                 @NotNull Torrent torrent,
                                                 @NotNull Peer peer,
                                                 @NotNull PeerCheckContext context) {
        try {
            if (module.isThreadSafe()) {
                context.setComment(false, "[PeerCheckService] Executing thread-safe module " + module.getName());
                return module.shouldBanPeer(torrent, peer, downloader, context);
            }

            module.getThreadLock().lock();
            try {
                context.setComment(false, "[PeerCheckService] Executing serialized module " + module.getName());
                return module.shouldBanPeer(torrent, peer, downloader, context);
            } finally {
                module.getThreadLock().unlock();
            }
        } catch (RuntimeException exception) {
            log.warn(tlUI(Lang.MODULE_CHECK_UNEXCEPTED_EXCEPTION, module.getName(), module.getClass().getName()), exception);
            return NO_ACTION_RESULT;
        }
    }

    private @NotNull CheckResult mergeResults(@NotNull List<CompletableFuture<CheckResult>> moduleResults) {
        CheckResult selected = null;
        for (CompletableFuture<CheckResult> resultFuture : moduleResults) {
            CheckResult result = resultFuture.join();
            if (selected == null
                    || result.action().ordinal() > selected.action().ordinal()
                    || (result.action().ordinal() == selected.action().ordinal()
                    && result.duration() > selected.duration())) {
                selected = result;
            }
        }
        return selected == null ? NO_ACTION_RESULT : selected;
    }

    private @Nullable CheckResult checkIfPossibleBadConfig(@NotNull Downloader downloader,
                                                           @NotNull Torrent torrent,
                                                           @NotNull Peer peer) {
        var node = downloaderServer.getIgnoreAddresses().elementsContaining(peer.getPeerAddress().getAddress());
        if (node == null) {
            return null;
        }
        if (isPeerHavePossibleBadNatConfig(peer)
                && !alertManager.identifierAlertExistsIncludeRead("downloader-nat-setup-error@" + downloader.getId())) {
            alertManager.publishAlert(
                    true,
                    AlertLevel.ERROR,
                    "downloader-nat-setup-error@" + downloader.getId(),
                    new TranslationComponent(Lang.DOWNLOADER_DOCKER_INCORRECT_NETWORK_DETECTED_TITLE),
                    new TranslationComponent(
                            Lang.DOWNLOADER_DOCKER_INCORRECT_NETWORK_DETECTED_DESCRIPTION,
                            downloader.getId(),
                            peer.getPeerAddress().getAddress().toCompressedString()
                    )
            );
        }
        return new CheckResult(
                getClass(),
                PeerAction.SKIP,
                0,
                new TranslationComponent("general-rule-ignored-address"),
                new TranslationComponent("general-reason-skip-ignored-peers"),
                StructuredData.create().add("type", "ignoredAddresses")
        );
    }

    private boolean isPeerHavePossibleBadNatConfig(@NotNull Peer peer) {
        if (peer.getFlags() == null
                || peer.getFlags().isFromIncoming()
                || !peer.getFlags().isOutgoingConnection()
                || peer.getFlags().isFromTracker()
                || peer.getFlags().isFromDHT()
                || peer.getFlags().isFromPEX()) {
            if (!peer.isHandshaking()) {
                var address = peer.getPeerAddress().getAddress();
                if (address.isIPv4Convertible()) {
                    address = address.toIPv4();
                }
                String addressString = address.toCompressedString();
                return (addressString.endsWith(".1") || addressString.endsWith(".0"))
                        && (address.isLocal() || address.isAnyLocal());
            }
        }
        return false;
    }

    private record PeerCheckKey(
            String downloaderId,
            String torrentHash,
            String peerAddress,
            int peerPort
    ) {
    }
}
