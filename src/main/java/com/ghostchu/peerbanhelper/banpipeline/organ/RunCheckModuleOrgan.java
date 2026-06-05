package com.ghostchu.peerbanhelper.banpipeline.organ;

import com.ghostchu.peerbanhelper.DownloaderServerImpl;
import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.banpipeline.BanOrgan;
import com.ghostchu.peerbanhelper.banpipeline.BanOrganCallback;
import com.ghostchu.peerbanhelper.banpipeline.data.CheckResultBatch;
import com.ghostchu.peerbanhelper.banpipeline.data.FetchedPeersBatch;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.FeatureModule;
import com.ghostchu.peerbanhelper.module.ModuleManager;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.module.RuleFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class RunCheckModuleOrgan extends BanOrgan<FetchedPeersBatch, CheckResultBatch> {
    private final ModuleManager moduleManager;
    private final AlertManager alertManager;
    private final DownloaderServerImpl downloaderServer;

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
    public void digest(FetchedPeersBatch input, Consumer<CheckResultBatch> outlet) throws RuntimeException {
        for (FeatureModule m : moduleManager.getModules()) {
            if (!(m instanceof RuleFeatureModule ruleFeatureModule)) {
                continue;
            }
            var downloader = input.downloader();
            var torrent = input.torrent();
            var peers = input.peers();
            AtomicInteger newSpawned = new AtomicInteger();
            peers.forEach(peer -> addMoreDigestingPrey(CompletableFuture.runAsync(() -> {
                        newSpawned.addAndGet(1);
                        var badConfigCheck = checkIfPossibleBadConfig(downloader, torrent, peer);
                        if (badConfigCheck != null) {
                            outlet.accept(new CheckResultBatch(downloader, torrent, peer, badConfigCheck));
                            return;
                        }
                        try {
                            if (ruleFeatureModule.isThreadSafe()) {
                                outlet.accept(new CheckResultBatch(downloader, torrent, peer, ruleFeatureModule.shouldBanPeer(torrent, peer, downloader)));
                            } else {
                                try {
                                    ruleFeatureModule.getThreadLock().lock();
                                    outlet.accept(new CheckResultBatch(downloader, torrent, peer, ruleFeatureModule.shouldBanPeer(torrent, peer, downloader)));
                                } finally {
                                    ruleFeatureModule.getThreadLock().unlock();
                                }
                            }
                        } catch (RuntimeException e) {
                            log.warn(tlUI(Lang.MODULE_CHECK_UNEXCEPTED_EXCEPTION, ruleFeatureModule.getName(), ruleFeatureModule.getClass().getName()), e);
                        }
                    }, digestEnergy)
            ));
            log.debug("[ORGAN-DEBUG] New spawned {} peers task checkmodule futures", newSpawned.get());
        }

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
