package com.ghostchu.peerbanhelper.database;

import org.jetbrains.annotations.Nullable;

public record BanLog(
        long banAt,
        long unbanAt,
        String peerIp,
        int peerPort,
        String peerId,
        String peerClientName,
        long peerUploaded,
        long peerDownloaded,
        double peerProgress,
        String torrentInfoHash,
        String torrentName,
        long torrentSize,
        @Nullable
        String module,
        String description
) {
}
