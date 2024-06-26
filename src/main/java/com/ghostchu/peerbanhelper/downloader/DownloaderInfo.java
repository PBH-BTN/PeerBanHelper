package com.ghostchu.peerbanhelper.downloader;

import org.jetbrains.annotations.NotNull;

public record DownloaderInfo(@NotNull String type,
                             @NotNull String endpoint,
                             @NotNull String version,
                             @NotNull String rpcVersion) {
}
