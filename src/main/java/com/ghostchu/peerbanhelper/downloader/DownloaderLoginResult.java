package com.ghostchu.peerbanhelper.downloader;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

@Data
@AllArgsConstructor
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
}
