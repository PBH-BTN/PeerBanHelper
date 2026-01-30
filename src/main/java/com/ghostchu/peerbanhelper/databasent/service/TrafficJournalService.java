package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.databasent.dto.TrafficDataComputed;
import com.ghostchu.peerbanhelper.databasent.service.impl.common.TrafficJournalServiceImpl;
import com.ghostchu.peerbanhelper.databasent.table.TrafficJournalEntity;
import com.ghostchu.peerbanhelper.downloader.DownloaderSpeedLimiter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;

public interface TrafficJournalService extends IService<TrafficJournalEntity> {
    void updateData(@NotNull String downloader, long overallDownloaded, long overallUploaded, long overallDownloadedProtocol, long overallUploadedProtocol);

    TrafficDataComputed getTodayData(@Nullable String downloader);

    List<TrafficDataComputed> getDayOffsetData(@Nullable String downloader, OffsetDateTime startAt, OffsetDateTime endAt);

    List<TrafficDataComputed> getAllDownloadersOverallData(OffsetDateTime start, OffsetDateTime end);

    List<TrafficDataComputed> getSpecificDownloaderOverallData(String downloadName, OffsetDateTime start, OffsetDateTime end);

    @NotNull TrafficJournalServiceImpl.SlidingWindowDynamicSpeedLimiter tweakSpeedLimiterBySlidingWindow(@Nullable String downloader, @NotNull DownloaderSpeedLimiter currentSetting,
                                                                                                         long thresholdBytes, long minSpeedBytesPerSecond, long maxSpeedBytesPerSecond);
}
