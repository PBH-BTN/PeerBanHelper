package com.ghostchu.peerbanhelper.downloader;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

@Data
@AllArgsConstructor
public class DownloaderLoginResult {
    @Getter
    private final Status status;
    private final TranslationComponent message;


    public boolean success() {
        return status == Status.SUCCESS;
    }

    public enum Status {
        SUCCESS,
        INCORRECT_CREDENTIAL,
        MISSING_COMPONENTS,
        NETWORK_ERROR,
        EXCEPTION
    }
}
