package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghostchu.peerbanhelper.databasent.dto.TrafficDataComputed;
import com.ghostchu.peerbanhelper.databasent.mapper.java.TrafficJournalMapper;
import com.ghostchu.peerbanhelper.databasent.service.TrafficJournalService;
import com.ghostchu.peerbanhelper.databasent.table.TrafficJournalEntity;
import com.ghostchu.peerbanhelper.downloader.DownloaderSpeedLimiter;
import com.ghostchu.peerbanhelper.util.TimeUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class TrafficJournalServiceImpl extends AbstractCommonService<TrafficJournalMapper, TrafficJournalEntity> implements TrafficJournalService {
    @Override
    public void updateData(@NotNull String downloader, long overallDownloaded, long overallUploaded, long overallDownloadedProtocol, long overallUploadedProtocol) {
        OffsetDateTime timestamp = TimeUtil.getStartOfHour(System.currentTimeMillis());
        TrafficJournalEntity entityInDb = baseMapper.selectOne(new LambdaQueryWrapper<TrafficJournalEntity>()
                .eq(TrafficJournalEntity::getDownloader, downloader)
                .eq(TrafficJournalEntity::getTimestamp, timestamp));
        if (entityInDb == null){
            entityInDb = new TrafficJournalEntity();
            entityInDb.setTimestamp(timestamp);
            entityInDb.setDownloader(downloader);
            entityInDb.setDataOverallDownloadedAtStart(overallDownloaded);
            entityInDb.setDataOverallUploadedAtStart(overallUploaded);
            entityInDb.setProtocolOverallDownloadedAtStart(overallDownloadedProtocol);
            entityInDb.setProtocolOverallUploadedAtStart(overallUploadedProtocol);
        }
        if (entityInDb.getDataOverallDownloaded() < overallDownloaded) {
            entityInDb.setDataOverallDownloaded(overallDownloaded);
        }
        if (entityInDb.getDataOverallUploaded() < overallUploaded) {
            entityInDb.setDataOverallUploaded(overallUploaded);
        }
        if (entityInDb.getProtocolOverallDownloaded() < overallDownloadedProtocol) {
            entityInDb.setProtocolOverallDownloaded(overallDownloadedProtocol);
        }
        if (entityInDb.getProtocolOverallUploaded() < overallUploadedProtocol) {
            entityInDb.setProtocolOverallUploaded(overallUploadedProtocol);
        }
        baseMapper.insertOrUpdate(entityInDb);
    }

    @Override
    public TrafficDataComputed getTodayData(@Nullable String downloader) {
        OffsetDateTime startOfToday = TimeUtil.getStartOfToday(System.currentTimeMillis());
        OffsetDateTime endOfToday = TimeUtil.getEndOfToday(System.currentTimeMillis());
        List<TrafficDataComputed> results;
        if (downloader == null || downloader.isBlank()) {
            results = getAllDownloadersOverallData(startOfToday, endOfToday).stream().toList();
        } else {
            results = getSpecificDownloaderOverallData(downloader, startOfToday, endOfToday).stream().toList();
        }
        if (results.isEmpty()) {
            return new TrafficDataComputed(startOfToday, 0, 0);
        } else {
            return new TrafficDataComputed(
                    startOfToday,
                    results.stream().mapToLong(TrafficDataComputed::getDataOverallUploaded).sum(),
                    results.stream().mapToLong(TrafficDataComputed::getDataOverallDownloaded).sum()
            );
        }
    }

    @Override
    public List<TrafficDataComputed> getDayOffsetData(String downloader, OffsetDateTime startAt, OffsetDateTime endAt) {
        List<TrafficDataComputed> results;
        if (downloader == null || downloader.isBlank()) {
            results = getAllDownloadersOverallData(startAt, endAt).stream().toList();
        } else {
            results = getSpecificDownloaderOverallData(downloader, startAt, endAt).stream().toList();
        }
        return results;
    }

    @Override
    public List<TrafficDataComputed> getAllDownloadersOverallData(OffsetDateTime start, OffsetDateTime end) {
        return baseMapper.selectAllDownloadersOverallData(start, end);
    }

    @Override
    public List<TrafficDataComputed> getSpecificDownloaderOverallData(String downloadName, OffsetDateTime start, OffsetDateTime end) {
        return baseMapper.selectSpecificDownloaderOverallData(downloadName, start, end);
    }

    @Override
    public @NotNull SlidingWindowDynamicSpeedLimiter tweakSpeedLimiterBySlidingWindow(@Nullable String downloader, @NotNull DownloaderSpeedLimiter currentSetting,
                                                                                      long thresholdBytes, long minSpeedBytesPerSecond, long maxSpeedBytesPerSecond) {
        SlidingWindowDynamicSpeedLimiter slidingWindowDynamicSpeedLimiter = new SlidingWindowDynamicSpeedLimiter();
        slidingWindowDynamicSpeedLimiter.setThreshold(thresholdBytes);
        slidingWindowDynamicSpeedLimiter.setMaxSpeed(maxSpeedBytesPerSecond);
        slidingWindowDynamicSpeedLimiter.setMinSpeed(minSpeedBytesPerSecond);
        // 假设滑动窗口为24小时
        Duration windowSize = Duration.ofHours(24);
        long windowSizeMillis = windowSize.toMillis();
        slidingWindowDynamicSpeedLimiter.setWindowSizeMillis(windowSizeMillis);

        OffsetDateTime endTimestamp = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        OffsetDateTime startTimestamp = endTimestamp.minus(windowSize);

        slidingWindowDynamicSpeedLimiter.setWindowStartTime(startTimestamp.toInstant().toEpochMilli());

        // 获取窗口内的流量数据
        List<TrafficDataComputed> trafficData = downloader == null
                ? getAllDownloadersOverallData(startTimestamp, endTimestamp)
                : getSpecificDownloaderOverallData(downloader, startTimestamp, endTimestamp);

        // 计算窗口内的总上传流量
        long totalUploadedBytes = trafficData.stream()
                .mapToLong(TrafficDataComputed::getDataOverallUploaded)
                .sum();
        slidingWindowDynamicSpeedLimiter.setUploadedInWindow(totalUploadedBytes);

        // 获取当前速度限制
        long currentSpeedLimit = currentSetting.upload();
        slidingWindowDynamicSpeedLimiter.setOldSpeedLimit(currentSpeedLimit);
        long newSpeed;

        if (totalUploadedBytes >= thresholdBytes) {
            // 应用节流策略 - 当达到或超过阈值时
            if (currentSpeedLimit <= minSpeedBytesPerSecond) {
                newSpeed = minSpeedBytesPerSecond;
                slidingWindowDynamicSpeedLimiter.setReachedMinimumSpeed(true);
            } else {
                // 计算减少因子 b = l_current / l
                double b = thresholdBytes > 0 ? (double) totalUploadedBytes / thresholdBytes : 1.0;
                newSpeed = Math.max(minSpeedBytesPerSecond, (long) (currentSpeedLimit / b));
                slidingWindowDynamicSpeedLimiter.setDecreaseFactor(b);
            }
        } else {
            // 应用解除节流策略 - 当未达到阈值时
            if ((currentSpeedLimit >= maxSpeedBytesPerSecond) && maxSpeedBytesPerSecond > 0) {
                newSpeed = maxSpeedBytesPerSecond;
            } else {
                // 计算增加因子 a = (l - l_current) / w，并转换为每秒字节数
                double a = (double) (thresholdBytes - totalUploadedBytes) / windowSizeMillis * 1000;
                newSpeed = Math.min(maxSpeedBytesPerSecond, Math.addExact(currentSpeedLimit, (long) a));
                slidingWindowDynamicSpeedLimiter.setIncreaseFactor(a);
            }
        }
        slidingWindowDynamicSpeedLimiter.setNewSpeedLimit(newSpeed);

        // 创建并返回新的速度限制设置
        return slidingWindowDynamicSpeedLimiter;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlidingWindowDynamicSpeedLimiter {
        private Long windowSizeMillis;
        private Long windowStartTime;
        private Long uploadedInWindow;
        private Long oldSpeedLimit;
        private Long newSpeedLimit;
        private Long threshold;
        private Long minSpeed;
        private Long maxSpeed;
        private Double increaseFactor;
        private Double decreaseFactor;
        private Boolean reachedMaximumSpeed;
        private Boolean reachedMinimumSpeed;
    }
}
