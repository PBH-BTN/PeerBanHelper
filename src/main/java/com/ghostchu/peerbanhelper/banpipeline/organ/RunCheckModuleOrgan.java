package com.ghostchu.peerbanhelper.banpipeline.organ;

import com.ghostchu.peerbanhelper.banpipeline.BanOrgan;
import com.ghostchu.peerbanhelper.banpipeline.BanOrganCallback;
import com.ghostchu.peerbanhelper.banpipeline.data.CheckResultBatch;
import com.ghostchu.peerbanhelper.banpipeline.data.FetchedPeersBatch;
import com.ghostchu.peerbanhelper.module.FeatureModule;
import com.ghostchu.peerbanhelper.module.ModuleManager;
import com.ghostchu.peerbanhelper.module.RuleFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
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

    public RunCheckModuleOrgan(Executor schedEnergy, Executor digestEnergy, @Nullable BanOrgan<?, FetchedPeersBatch> in,
                               @Nullable BiConsumer<BanOrgan<FetchedPeersBatch, CheckResultBatch>, BanOrganCallback<FetchedPeersBatch>> gastroscopy,
                               long maxDigestDuration, TimeUnit digestTimeUnit,
                               ModuleManager moduleManager) {
        super(schedEnergy, digestEnergy, in, gastroscopy, maxDigestDuration, digestTimeUnit);
        this.moduleManager = moduleManager;
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
}
