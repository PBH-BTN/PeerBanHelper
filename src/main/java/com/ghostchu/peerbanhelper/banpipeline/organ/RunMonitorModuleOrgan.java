package com.ghostchu.peerbanhelper.banpipeline.organ;

import com.ghostchu.peerbanhelper.banpipeline.BanOrgan;
import com.ghostchu.peerbanhelper.banpipeline.BanOrganCallback;
import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.banpipeline.data.FetchedPeersBatch;
import com.ghostchu.peerbanhelper.module.BatchMonitorFeatureModule;
import com.ghostchu.peerbanhelper.module.ModuleManager;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class RunMonitorModuleOrgan extends BanOrgan<FetchedPeersBatch, FetchedPeersBatch> {
    private final ModuleManager moduleManager;

    public RunMonitorModuleOrgan(Executor schedEnergy, Executor digestEnergy, @Nullable BanOrgan<?, FetchedPeersBatch> in,
                                 @Nullable BiConsumer<BanOrgan<FetchedPeersBatch, FetchedPeersBatch>, BanOrganCallback<FetchedPeersBatch>> gastroscopy,
                                 long maxDigestDuration, TimeUnit digestTimeUnit,
                                 ModuleManager moduleManager) {
        super(schedEnergy, digestEnergy, in, gastroscopy, maxDigestDuration, digestTimeUnit);
        this.moduleManager = moduleManager;
    }

    @Override
    public void digest(FetchedPeersBatch input, Consumer<FetchedPeersBatch> outlet,  PipelineTask<?> wrapper) throws RuntimeException {
        wrapper.setComment(false, "Parallel notifying batch monitor modules for torrent: " + input.torrent().getId());
        moduleManager.getModules().stream().filter(m -> m instanceof BatchMonitorFeatureModule)
                .parallel()
                .forEach(m -> ((BatchMonitorFeatureModule) m).onPeersRetrieved(input.downloader(), input.torrent(), input.peers(), wrapper));
        outlet.accept(input);
    }
}
