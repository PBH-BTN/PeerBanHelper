package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.databasent.dto.PeerBanCount;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;

public interface HistoryService extends IService<HistoryEntity> {
    public IPage<PeerBanCount> getBannedIps(@NotNull Page<PeerBanCount> page, @Nullable String filter);

    long countHistoriesByTorrentId(@NotNull Long id);

    long countHistoriesByIp(@NotNull InetAddress inetAddress);

    IPage<HistoryEntity> queryBanHistoryByIp(@NotNull Page<HistoryEntity> pageable, @NotNull InetAddress ip, @NotNull Orderable orderBy);
}
