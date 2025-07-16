package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.database.table.TorrentEntity;

public record TorrentEntityDTO(Long id, String infoHash, String name, Long size, Boolean privateTorrent) {
    public static TorrentEntityDTO from(TorrentEntity torrentEntity){
        return new TorrentEntityDTO(
                torrentEntity.getId(),
                torrentEntity.getInfoHash(),
                torrentEntity.getName(),
                torrentEntity.getSize(),
                torrentEntity.getPrivateTorrent()
        );
    }
}
