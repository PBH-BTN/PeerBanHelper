package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.databasent.dto.PeerBanCount;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;

public interface HistoryService extends IService<HistoryEntity> {
    IPage<PeerBanCount> getBannedIps(@NotNull Pageable pageable, @Nullable String filter);

    long countHistoriesByTorrentId(@NotNull Long id);

    long countHistoriesByIp(@NotNull InetAddress inetAddress);
}
