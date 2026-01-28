package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TorrentService extends IService<TorrentEntity> {
    @NotNull TorrentEntity createIfNotExists(@NotNull TorrentEntity torrent);

    @Nullable TorrentEntity queryByInfoHash(@NotNull String infoHash);

    IPage<TorrentEntity> search(Page<TorrentEntity> page, String keyword, Orderable normalSort, @Nullable String statsSortField, boolean statsSortAsc);
}
