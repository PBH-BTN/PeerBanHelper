package com.ghostchu.peerbanhelper.banpipeline.organ;

import com.ghostchu.peerbanhelper.banpipeline.BanOrgan;
import com.ghostchu.peerbanhelper.banpipeline.BanOrganCallback;
import com.ghostchu.peerbanhelper.banpipeline.data.FetchedTorrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class TorrentsFetchOrgan extends BanOrgan<Downloader, FetchedTorrent> {
    public TorrentsFetchOrgan(Executor schedEnergy, Executor digestEnergy, @Nullable BanOrgan<?, Downloader> in, @Nullable BiConsumer<BanOrgan<Downloader, FetchedTorrent>, BanOrganCallback<Downloader>> gastroscopy, long maxDigestDuration, TimeUnit digestTimeUnit) {
        super(schedEnergy, digestEnergy, in, gastroscopy, maxDigestDuration, digestTimeUnit);
    }

    @Override
    public void digest(Downloader input, Consumer<FetchedTorrent> outlet) throws RuntimeException {
        input.getTorrents().forEach(t -> outlet.accept(new FetchedTorrent(input, t)));
    }
}
