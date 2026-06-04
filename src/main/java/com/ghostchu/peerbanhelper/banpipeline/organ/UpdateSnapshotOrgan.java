package com.ghostchu.peerbanhelper.banpipeline.organ;

import com.ghostchu.peerbanhelper.DownloaderServerImpl;
import com.ghostchu.peerbanhelper.banpipeline.BanOrgan;
import com.ghostchu.peerbanhelper.banpipeline.BanOrganCallback;
import com.ghostchu.peerbanhelper.banpipeline.data.FetchedPeersBatch;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class UpdateSnapshotOrgan extends BanOrgan<FetchedPeersBatch, FetchedPeersBatch> {

    private final DownloaderServerImpl downloaderServer;
    private final Map<Downloader, Map<Torrent, List<Peer>>> snapshot = Collections.synchronizedMap(new HashMap<>());

    public UpdateSnapshotOrgan(Executor schedEnergy, Executor digestEnergy, @Nullable BanOrgan<?, FetchedPeersBatch> in, @Nullable BiConsumer<BanOrgan<FetchedPeersBatch, FetchedPeersBatch>, BanOrganCallback<FetchedPeersBatch>> gastroscopy, long maxDigestDuration, TimeUnit digestTimeUnit,
                               DownloaderServerImpl downloaderServer) {
        super(schedEnergy, digestEnergy, in, gastroscopy, maxDigestDuration, digestTimeUnit);
        this.downloaderServer = downloaderServer;
    }

    @Override
    public void digest(FetchedPeersBatch input, Consumer<FetchedPeersBatch> outlet) throws RuntimeException {
        var downloader = snapshot.getOrDefault(input.downloader(), Collections.synchronizedMap(new HashMap<>()));
        var torrent = downloader.getOrDefault(input.torrent(), Collections.synchronizedList(new ArrayList<>()));
        torrent.addAll(input.peers());
        downloader.put(input.torrent(), torrent);
        snapshot.put(input.downloader(), downloader);
        outlet.accept(input);
    }


    @Override
    public void endSession() {
        super.endSession();
        downloaderServer.updateLivePeers(snapshot);
        snapshot.clear();
    }
}
