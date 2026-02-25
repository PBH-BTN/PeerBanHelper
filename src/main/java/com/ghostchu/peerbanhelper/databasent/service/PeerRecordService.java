package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.databasent.dto.ClientAnalyseResult;
import com.ghostchu.peerbanhelper.databasent.dto.IPAddressTimeSeen;
import com.ghostchu.peerbanhelper.databasent.dto.IPAddressTotalTraffic;
import com.ghostchu.peerbanhelper.databasent.service.impl.common.PeerRecordServiceImpl;
import com.ghostchu.peerbanhelper.databasent.table.PeerRecordEntity;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface PeerRecordService extends IService<PeerRecordEntity> {
    List<PeerRecordEntity> getRecordsBetween(OffsetDateTime start, OffsetDateTime end, String downloader);

    void flushToDatabase(PeerRecordServiceImpl.BatchHandleTasks tasks);

    long sessionBetween(@NotNull String downloader, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt);

    @NotNull Page<PeerRecordEntity> getPendingSubmitPeerRecords(@NotNull Pageable pageable, @NotNull OffsetDateTime afterThan);

    long countRecordsByIp(@NotNull InetAddress inetAddress);

    IPAddressTotalTraffic queryAddressTotalTraffic(@NotNull InetAddress inet);

    IPAddressTimeSeen queryAddressTimeSeen(@NotNull InetAddress inet);

    @NotNull IPage<PeerRecordEntity> queryAccessHistoryByIp(@NotNull Page<PeerRecordEntity> page, @NotNull InetAddress ip, @NotNull Orderable orderable);

    @NotNull Page<ClientAnalyseResult> queryClientAnalyse(@NotNull Page<ClientAnalyseResult> page, @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt, @Nullable String downloader, @NotNull String orderBySql);

    long countRecordsByTorrentId(Long id);

    @NotNull Page<PeerRecordEntity> queryAccessHistoryByTorrentId(@NotNull Page<PeerRecordEntity> page, @NotNull Long id, @NotNull Orderable orderable);

    Map<Long, Long> countByTorrentIds(@NotNull List<Long> torrentIds);

    List<String> getDistinctIps(@NotNull java.time.OffsetDateTime startAt,
                                @NotNull java.time.OffsetDateTime endAt,
                                @Nullable String downloader);

    long cleanup(@NotNull OffsetDateTime before);
}
