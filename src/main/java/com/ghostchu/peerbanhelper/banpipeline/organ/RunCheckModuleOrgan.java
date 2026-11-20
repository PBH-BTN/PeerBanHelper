package com.ghostchu.peerbanhelper.banpipeline.organ;

import com.ghostchu.peerbanhelper.DownloaderServerImpl;
import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.banpipeline.BanOrgan;
import com.ghostchu.peerbanhelper.banpipeline.BanOrganCallback;
import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.banpipeline.data.CheckResultBatch;
import com.ghostchu.peerbanhelper.banpipeline.data.FetchedPeersBatch;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.*;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class RunCheckModuleOrgan extends BanOrgan<FetchedPeersBatch, CheckResultBatch> {
    private final ModuleManager moduleManager;
    private final AlertManager alertManager;
    private final DownloaderServerImpl downloaderServer;
    /*
   判定执行线程池
    */
    private final ExecutorService jobRunExecutor = Executors.newWorkStealingPool(Math.max(8, Runtime.getRuntime().availableProcessors() - 1));

    public RunCheckModuleOrgan(Executor schedEnergy, Executor digestEnergy, @Nullable BanOrgan<?, FetchedPeersBatch> in,
                               @Nullable BiConsumer<BanOrgan<FetchedPeersBatch, CheckResultBatch>, BanOrganCallback<FetchedPeersBatch>> gastroscopy,
                               long maxDigestDuration, TimeUnit digestTimeUnit,
                               ModuleManager moduleManager, AlertManager alertManager, DownloaderServerImpl downloaderServer) {
        super(schedEnergy, digestEnergy, in, gastroscopy, maxDigestDuration, digestTimeUnit);
        this.moduleManager = moduleManager;
        this.alertManager = alertManager;
        this.downloaderServer = downloaderServer;
    }

    @Override
    public void digest(FetchedPeersBatch input, Consumer<CheckResultBatch> outlet, PipelineTask<?> wrapper) throws RuntimeException {
        var rules = moduleManager.getModules().stream()
                .filter(RuleFeatureModule.class::isInstance).map(RuleFeatureModule.class::cast).toList();
        if (rules.isEmpty()) return;
        // Bound scheduling overhead while allowing a single large torrent to use several workers.
        final int batchSize = 32;
        for (int start = 0; start < input.peers().size(); start += batchSize) {
            var peers = input.peers().subList(start, Math.min(input.peers().size(), start + batchSize));
            var batch = new PipelineTask<Void>(null, this, "Checking peers");
            batch.setDelegate(CompletableFuture.runAsync(() -> {
                for (Peer peer : peers) {
                    try {
                        CheckResult result = checkPeer(input, peer, rules, batch);
                        if (result != null) outlet.accept(new CheckResultBatch(input.downloader(), input.torrent(), peer, result));
                    } catch (RuntimeException e) {
                        log.warn("Unable to check peer {}", peer.getPeerAddress(), e);
                    }
                }
            }, jobRunExecutor));
            addMoreDigestingPrey(batch);
        }
    }

    private CheckResult checkPeer(FetchedPeersBatch input, Peer peer, List<RuleFeatureModule> rules, PipelineTask<?> task) {
        var ignored = checkIfPossibleBadConfig(input.downloader(), input.torrent(), peer);
        if (ignored != null) return ignored;
        CheckResult best = null;
        for (var rule : rules) {
            try {
                task.setComment(false, rule.getName());
                CheckResult result;
                if (rule.isThreadSafe()) {
                    result = rule.shouldBanPeer(input.torrent(), peer, input.downloader(), task);
                } else {
                    var lock = rule.getThreadLock();
                    lock.lock();
                    try {
                        result = rule.shouldBanPeer(input.torrent(), peer, input.downloader(), task);
                    } finally {
                        lock.unlock();
                    }
                }
                best = CheckResult.preferred(best, result);
            } catch (RuntimeException e) {
                log.warn(tlUI(Lang.MODULE_CHECK_UNEXCEPTED_EXCEPTION, rule.getName(), rule.getClass().getName()), e);
            }
        }
        return best;
    }

    @Nullable
    private CheckResult checkIfPossibleBadConfig(Downloader downloader, Torrent torrent, Peer peer) {
        var node = downloaderServer.getIgnoreAddresses().elementsContaining(peer.getPeerAddress().getAddress());
        if (node != null) {
            if (isPeerHavePossibleBadNatConfig(peer)) {
                if (!alertManager.identifierAlertExistsIncludeRead("downloader-nat-setup-error@" + downloader.getId())) {
                    alertManager.publishAlert(true, AlertLevel.ERROR, "downloader-nat-setup-error@" + downloader.getId(),
                            new TranslationComponent(Lang.DOWNLOADER_DOCKER_INCORRECT_NETWORK_DETECTED_TITLE),
                            new TranslationComponent(Lang.DOWNLOADER_DOCKER_INCORRECT_NETWORK_DETECTED_DESCRIPTION, downloader.getId(), peer.getPeerAddress().getAddress().toCompressedString()));
                }
            }
            return new CheckResult(getClass(), PeerAction.SKIP, 0, new TranslationComponent("general-rule-ignored-address"), new TranslationComponent("general-reason-skip-ignored-peers"), StructuredData.create().add("type", "ignoredAddresses"));
        }
        return null;
    }

    @Override
    public void endSession() {
        jobRunExecutor.shutdown();
        super.endSession();
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
                var addrStr = addr.toCompressedString();
                return (addrStr.endsWith(".1") || addrStr.endsWith(".0"))  // check for possible gateway in-correct forward without oshi calls, tricky
                        && (addr.isLocal() || addr.isAnyLocal());
            }
        }
        return false;
    }

}
