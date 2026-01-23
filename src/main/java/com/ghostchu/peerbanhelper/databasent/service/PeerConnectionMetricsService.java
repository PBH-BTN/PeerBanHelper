package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.databasent.table.PeerConnectionMetricsEntity;
import com.ghostchu.peerbanhelper.databasent.table.PeerConnectionMetricsTrackEntity;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.PeerConnectionMetricsDTO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;

public interface PeerConnectionMetricsService extends IService<PeerConnectionMetricsEntity> {

	long getGlobalTotalConnectionsCount(@NotNull Timestamp startAt, @NotNull Timestamp endAt);

	List<PeerConnectionMetricsDTO> getMetricsSince(@NotNull Timestamp sinceAt, @NotNull Timestamp untilAt, @Nullable String downloader);

	void saveAggregating(@NotNull List<PeerConnectionMetricsEntity> buffer, boolean overwrite);

	List<PeerConnectionMetricsEntity> aggregating(@NotNull List<PeerConnectionMetricsTrackEntity> fullPeerSessions);

	void removeOutdatedData(OffsetDateTime beforeAt);
}
