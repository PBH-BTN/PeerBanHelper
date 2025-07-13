package com.ghostchu.peerbanhelper.downloader;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;


public record DownloaderLoginResult(@Getter com.ghostchu.peerbanhelper.downloader.DownloaderLoginResult.Status status,
                                    TranslationComponent message) {
    public boolean success() {
        return status == Status.SUCCESS;
    }

    public enum Status {
        SUCCESS,
        PAUSED,
        INCORRECT_CREDENTIAL,
        MISSING_COMPONENTS,
        NETWORK_ERROR,
        EXCEPTION,
        REQUIRE_TAKE_ACTIONS,
    }

    @Override
    public @NotNull String toString() {
        return "DownloaderLoginResult{" +
                "status=" + status +
                ", message=" + message +
                '}';
    }
}
