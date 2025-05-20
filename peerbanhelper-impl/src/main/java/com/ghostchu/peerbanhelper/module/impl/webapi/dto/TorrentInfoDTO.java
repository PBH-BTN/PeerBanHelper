package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

public record TorrentInfoDTO(
        String infoHash,
        String name,
        long size,
        long peerBanCount,
        long peerAccessCount
) {

}
