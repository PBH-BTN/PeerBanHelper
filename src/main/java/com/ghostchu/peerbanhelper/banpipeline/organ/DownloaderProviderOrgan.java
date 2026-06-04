package com.ghostchu.peerbanhelper.banpipeline.organ;

import com.ghostchu.peerbanhelper.banpipeline.BanOrgan;
import com.ghostchu.peerbanhelper.banpipeline.BanOrganCallback;
import com.ghostchu.peerbanhelper.banpipeline.OrganLifeCycleStatus;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderManager;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class DownloaderProviderOrgan extends BanOrgan<Void, Downloader> {


    public DownloaderProviderOrgan(
            DownloaderManager downloaderManager,
            Executor schedEnergy, Executor digestEnergy, BanOrgan<?, Void> in, BiConsumer<BanOrgan<Void, Downloader>,
            BanOrganCallback<Void>> gastroscopy, long maxDigestDuration, TimeUnit digestTimeUnit){
        super(schedEnergy, digestEnergy, in, gastroscopy, maxDigestDuration, digestTimeUnit);
        outlet.addAll(downloaderManager.getDownloaders());
    }

    @Override
    public void digest(Void input, Consumer<Downloader> outlet) throws RuntimeException {

    }

    @Override
    public boolean checkIfRunningTaskEmpty() {
        return true;
    }

}
