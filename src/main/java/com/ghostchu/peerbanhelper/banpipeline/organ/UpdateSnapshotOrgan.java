package com.ghostchu.peerbanhelper.banpipeline.organ;

import com.ghostchu.peerbanhelper.DownloaderServerImpl;
import com.ghostchu.peerbanhelper.banpipeline.BanOrgan;
import com.ghostchu.peerbanhelper.banpipeline.BanOrganCallback;
import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.banpipeline.data.FetchedPeersBatch;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class UpdateSnapshotOrgan extends BanOrgan<FetchedPeersBatch, FetchedPeersBatch> {

    private final DownloaderServerImpl downloaderServer;
    private Map<Downloader, Map<Torrent, List<Peer>>> snapshot = new HashMap<>();

    public UpdateSnapshotOrgan(Executor schedEnergy, Executor digestEnergy, @Nullable BanOrgan<?, FetchedPeersBatch> in, @Nullable BiConsumer<BanOrgan<FetchedPeersBatch, FetchedPeersBatch>, BanOrganCallback<FetchedPeersBatch>> gastroscopy, long maxDigestDuration, TimeUnit digestTimeUnit,
                               DownloaderServerImpl downloaderServer) {
        super(schedEnergy, digestEnergy, in, gastroscopy, maxDigestDuration, digestTimeUnit);
        this.downloaderServer = downloaderServer;
    }

    @Override
    public void digest(FetchedPeersBatch input, Consumer<FetchedPeersBatch> outlet, PipelineTask<?> wrapper) throws RuntimeException {
        wrapper.setComment(false, "Filling collections with fresh data.");
        synchronized (this) {
            if (snapshot == null) return;
            snapshot.computeIfAbsent(input.downloader(), _ -> new HashMap<>())
                    .computeIfAbsent(input.torrent(), _ -> new ArrayList<>()).addAll(input.peers());
        }
        outlet.accept(input);
    }


    @Override
    public void endSession() {
        super.endSession();
        Map<Downloader, Map<Torrent, List<Peer>>> completed;
        synchronized (this) {
            completed = snapshot;
            snapshot = null;
        }
        if (completed != null) downloaderServer.updateLivePeers(completed);
    }
}
