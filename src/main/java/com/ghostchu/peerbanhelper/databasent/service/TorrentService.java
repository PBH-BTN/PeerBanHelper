package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TorrentService extends IService<TorrentEntity> {
    @NotNull TorrentEntity createIfNotExists(@NotNull TorrentEntity torrent);

    @Nullable TorrentEntity queryByInfoHash(@NotNull String infoHash);
}
