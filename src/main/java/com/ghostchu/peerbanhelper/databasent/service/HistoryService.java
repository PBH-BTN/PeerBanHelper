package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.databasent.dto.PeerBanCount;
import com.ghostchu.peerbanhelper.databasent.dto.UniversalFieldNumResult;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

public interface HistoryService extends IService<HistoryEntity> {
    IPage<PeerBanCount> getBannedIps(@NotNull Page<PeerBanCount> page, @Nullable String filter);

    long deleteExpiredLogs(int keepDays);

    long countHistoriesByTorrentId(@NotNull Long id);

    long countHistoriesByIp(@NotNull InetAddress inetAddress);

    IPage<HistoryEntity> queryBanHistoryByIp(@NotNull Page<HistoryEntity> pageable, @NotNull InetAddress ip, @NotNull Orderable orderBy);

    IPage<HistoryEntity> queryBanHistoryByTorrentId(@NotNull Page<HistoryEntity> pageable, @NotNull Long torrentId, @NotNull Orderable orderBy);

    List<UniversalFieldNumResult> countField(@NotNull String field, double percentFilter, @Nullable String downloader, @Nullable Integer substringLength);

    List<UniversalFieldNumResult> sumField(@NotNull String field, double percentFilter, @Nullable String downloader, @Nullable Integer substringLength);

    IPage<HistoryEntity> getBanLogs(Page<HistoryEntity> pageRequest, Orderable orderable);

    Map<Long, Long> countByTorrentIds(@NotNull List<Long> torrentIds);

    List<String> getDistinctIps(@NotNull java.time.OffsetDateTime start,
                                @NotNull java.time.OffsetDateTime end,
                                @Nullable String downloader);
}
