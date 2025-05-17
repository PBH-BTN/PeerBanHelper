package com.ghostchu.peerbanhelper.api.downloader;

import com.ghostchu.peerbanhelper.api.text.TranslationComponent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

@Data
@AllArgsConstructor
public final class DownloaderLoginResult {
    @Getter
    private final Status status;
    private final TranslationComponent message;


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
