package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.downloader.DownloaderBasicInfo;

import java.net.InetAddress;
import java.time.OffsetDateTime;

public record PeerRecordEntityDTO(
        Long id,
        InetAddress address,
        Integer port,
        TorrentEntityDTO torrent,
        DownloaderBasicInfo downloader,
        String peerId,
        String clientName,
        long uploaded,
        long uploadedOffset,
        long uploadSpeed,
        long downloaded,
        long downloadedOffset,
        long downloadSpeed,
        String lastFlags,
        OffsetDateTime firstTimeSeen,
        OffsetDateTime lastTimeSeen
) {

}
