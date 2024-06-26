package com.ghostchu.peerbanhelper.downloader;

import org.jetbrains.annotations.NotNull;

public record DownloaderBasicAuth(
        @NotNull String urlPrefix,
        @NotNull String username,
        @NotNull String password) {
}
