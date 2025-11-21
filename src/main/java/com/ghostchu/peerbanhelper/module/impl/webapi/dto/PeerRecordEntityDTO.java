package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.downloader.DownloaderBasicInfo;

import java.sql.Timestamp;

public record PeerRecordEntityDTO(
        Long id,
        String address,
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
        Timestamp firstTimeSeen,
        Timestamp lastTimeSeen
) {

}
