package com.ghostchu.peerbanhelper.banpipeline.organ;

import com.ghostchu.peerbanhelper.banpipeline.BanOrgan;
import com.ghostchu.peerbanhelper.banpipeline.BanOrganCallback;
import com.ghostchu.peerbanhelper.banpipeline.PeerCheckService;
import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.banpipeline.data.CheckResultBatch;
import com.ghostchu.peerbanhelper.banpipeline.data.FetchedPeersBatch;
import com.ghostchu.peerbanhelper.module.PeerCheckContext;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class RunCheckModuleOrgan extends BanOrgan<FetchedPeersBatch, CheckResultBatch> {
    private final PeerCheckService peerCheckService;

    public RunCheckModuleOrgan(Executor schedEnergy,
                               Executor digestEnergy,
                               @Nullable BanOrgan<?, FetchedPeersBatch> in,
                               @Nullable BiConsumer<BanOrgan<FetchedPeersBatch, CheckResultBatch>, BanOrganCallback<FetchedPeersBatch>> gastroscopy,
                               long maxDigestDuration,
                               TimeUnit digestTimeUnit,
                               PeerCheckService peerCheckService) {
        super(schedEnergy, digestEnergy, in, gastroscopy, maxDigestDuration, digestTimeUnit);
        this.peerCheckService = peerCheckService;
    }

    @Override
    public void digest(FetchedPeersBatch input,
                       Consumer<CheckResultBatch> outlet,
                       PipelineTask<?> wrapper) throws RuntimeException {
        PeerCheckContext context = wrapper;
        for (var peer : input.peers()) {
            var resultFuture = peerCheckService.checkPeerAsync(
                    input.downloader(),
                    input.torrent(),
                    peer,
                    context
            ).thenAccept(outlet::accept);
            addMoreDigestingPrey(new PipelineTask<>(
                    resultFuture,
                    this,
                    "Waiting for shared peer check result"
            ));
        }
    }
}
