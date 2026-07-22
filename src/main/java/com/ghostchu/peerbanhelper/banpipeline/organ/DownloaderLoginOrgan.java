package com.ghostchu.peerbanhelper.banpipeline.organ;

import com.ghostchu.peerbanhelper.banpipeline.BanOrgan;
import com.ghostchu.peerbanhelper.banpipeline.BanOrganCallback;
import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class DownloaderLoginOrgan extends BanOrgan<Downloader, Downloader> {

    public DownloaderLoginOrgan(Executor schedEnergy, Executor digestEnergy, @Nullable BanOrgan<?, Downloader> in,
                                @Nullable BiConsumer<BanOrgan<Downloader, Downloader>,
                                        BanOrganCallback<Downloader>> gastroscopy,
                                long maxDigestDuration, TimeUnit digestTimeUnit) {
        super(schedEnergy, digestEnergy, in, gastroscopy, maxDigestDuration, digestTimeUnit);
    }

    @Override
    public void digest(Downloader downloader, Consumer<Downloader> outlet, PipelineTask<?> wrapper) throws RuntimeException {
        wrapper.setComment(true, "Try to login downloader: " + downloader.getName() + " (" + downloader.getEndpoint() + ").");
        var loginResult = downloader.login();
        wrapper.setComment(false, "Processing downloader: " + downloader.getName() + " (" + downloader.getEndpoint() + ") login result...");
        if (!loginResult.success()) {
            if (loginResult.status() != DownloaderLoginResult.Status.PAUSED) {
                log.error(tlUI(Lang.ERR_CLIENT_LOGIN_FAILURE_SKIP, downloader.getName(), downloader.getEndpoint(), tlUI(loginResult.message())));
                downloader.setLastStatus(DownloaderLastStatus.ERROR, loginResult.message());
            }
        } else {
            downloader.setLastStatus(DownloaderLastStatus.HEALTHY, loginResult.message());
            outlet.accept(downloader);
        }
    }
}
