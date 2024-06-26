package com.ghostchu.peerbanhelper.database;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record BanLog(
        long banAt,
        long unbanAt,
        @NotNull String peerIp,
        int peerPort,
        @Nullable String peerId,
        @Nullable String peerClientName,
        long peerUploaded,
        long peerDownloaded,
        double peerProgress,
        @NotNull String torrentInfoHash,
        @NotNull String torrentName,
        long torrentSize,
        @Nullable String module,
        @NotNull String description
) {
}
