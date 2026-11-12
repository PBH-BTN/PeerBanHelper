package com.ghostchu.peerbanhelper.banpipeline.organ;

import com.ghostchu.peerbanhelper.banpipeline.BanOrgan;
import com.ghostchu.peerbanhelper.banpipeline.BanOrganCallback;
import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.banpipeline.data.FetchedPeersBatch;
import com.ghostchu.peerbanhelper.banpipeline.data.FetchedTorrent;
import com.ghostchu.peerbanhelper.downloader.DownloaderRequestLock;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class PeersFetchOrgan extends BanOrgan<FetchedTorrent, FetchedPeersBatch> {
    private final DownloaderRequestLock requestLock;

    public PeersFetchOrgan(Executor schedEnergy, Executor digestEnergy, @Nullable BanOrgan<?, FetchedTorrent> in,
                           DownloaderRequestLock requestLock,
                           @Nullable BiConsumer<BanOrgan<FetchedTorrent, FetchedPeersBatch>, BanOrganCallback<FetchedTorrent>> gastroscopy,
                           long maxDigestDuration, TimeUnit digestTimeUnit) {
        super(schedEnergy, digestEnergy, in, gastroscopy, maxDigestDuration, digestTimeUnit);
        this.requestLock = requestLock;
    }

    @Override
    public void digest(FetchedTorrent input, Consumer<FetchedPeersBatch> outlet, PipelineTask<?> wrapper) throws RuntimeException {
        try {
            wrapper.setComment(false, "Fetching peers for torrent: " + input.torrent().getId() + ", waiting Semaphore...");
            input.downloader().getConcurrentRequestControlSemaphore().acquire();
            wrapper.setComment(true, "Fetching peers for torrent: " + input.torrent().getId() + ", execute HTTP requests...");
            var peers = requestLock.withReadLock(input.downloader(), () -> input.downloader().getPeers(input.torrent()));
            wrapper.setComment(false, "Fetching peers for torrent: " + input.torrent().getId() + ", waiting for outlet...");
            outlet.accept(new FetchedPeersBatch(input.downloader(), input.torrent(), peers));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } finally {
            input.downloader().getConcurrentRequestControlSemaphore().release();
        }
    }
}
