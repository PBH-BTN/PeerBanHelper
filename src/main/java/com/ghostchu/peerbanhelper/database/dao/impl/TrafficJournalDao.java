package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.TrafficJournalEntity;
import com.ghostchu.peerbanhelper.downloader.DownloaderSpeedLimiter;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.j256.ormlite.support.ConnectionSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Component
public final class TrafficJournalDao extends AbstractPBHDao<TrafficJournalEntity, Long> {

    public TrafficJournalDao(@Autowired ConnectionSource database) throws SQLException {
        super(database, TrafficJournalEntity.class);
    }

    @NotNull
    public SlidingWindowDynamicSpeedLimiter tweakSpeedLimiterBySlidingWindow(@Nullable String downloader, @NotNull DownloaderSpeedLimiter currentSetting,
                                                                             long thresholdBytes, long minSpeedBytesPerSecond, long maxSpeedBytesPerSecond) throws Exception {
        SlidingWindowDynamicSpeedLimiter slidingWindowDynamicSpeedLimiter = new SlidingWindowDynamicSpeedLimiter();
        slidingWindowDynamicSpeedLimiter.setThreshold(thresholdBytes);
        slidingWindowDynamicSpeedLimiter.setMaxSpeed(maxSpeedBytesPerSecond);
        slidingWindowDynamicSpeedLimiter.setMinSpeed(minSpeedBytesPerSecond);
        // 假设滑动窗口为24小时
        long windowSizeMillis = 24 * 60 * 60 * 1000; // 24小时(毫秒)
        slidingWindowDynamicSpeedLimiter.setWindowSizeMillis(windowSizeMillis);
        long currentTime = System.currentTimeMillis();
        long windowStartTime = currentTime - windowSizeMillis;
        slidingWindowDynamicSpeedLimiter.setWindowStartTime(windowStartTime);

        Timestamp startTimestamp = new Timestamp(windowStartTime);
        Timestamp endTimestamp = new Timestamp(currentTime);

        // 获取窗口内的流量数据
        List<TrafficDataComputed> trafficData = downloader == null ? getAllDownloadersOverallData(startTimestamp, endTimestamp) : getSpecificDownloaderOverallData(downloader, startTimestamp, endTimestamp);

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
                double b = (double) totalUploadedBytes / thresholdBytes;
                newSpeed = (long) (currentSpeedLimit / b);
                // 确保不低于最小速度
                newSpeed = Math.max(newSpeed, minSpeedBytesPerSecond);
                slidingWindowDynamicSpeedLimiter.setDecreaseFactor(b);
            }
        } else {
            // 应用解除节流策略 - 当未达到阈值时
            if ((currentSpeedLimit >= maxSpeedBytesPerSecond) && maxSpeedBytesPerSecond > 0) {
                newSpeed = maxSpeedBytesPerSecond;
            } else {
                // 计算增加因子 a = (l - l_current) / w，并转换为每秒字节数
                double a = (double) (thresholdBytes - totalUploadedBytes) / windowSizeMillis * 1000;
                newSpeed = (long) (currentSpeedLimit + a);
                // 确保不超过最大速度
                newSpeed = Math.min(newSpeed, maxSpeedBytesPerSecond);
                slidingWindowDynamicSpeedLimiter.setIncreaseFactor(a);
            }
        }
        slidingWindowDynamicSpeedLimiter.setNewSpeedLimit(newSpeed);

        if (slidingWindowDynamicSpeedLimiter.getNewSpeedLimit() < 1) { // 0 = 无限制
            slidingWindowDynamicSpeedLimiter.setNewSpeedLimit(1L);
        }
        // 创建并返回新的速度限制设置
        return slidingWindowDynamicSpeedLimiter;
    }

    public TrafficDataComputed getTodayData(String downloader) throws Exception {
        Timestamp startOfToday = new Timestamp(MiscUtil.getStartOfToday(System.currentTimeMillis()));
        Timestamp endOfToday = new Timestamp(MiscUtil.getEndOfToday(System.currentTimeMillis()));
        List<TrafficDataComputed> results;
        if (downloader == null || downloader.isBlank()) {
            results = getAllDownloadersOverallData(startOfToday, endOfToday).stream().toList();
        } else {
            results = getSpecificDownloaderOverallData(downloader, startOfToday, endOfToday).stream().toList();
        }
        if (results.isEmpty()) {
            return new TrafficDataComputed(startOfToday, 0, 0);
        } else {
            return new TrafficDataComputed(startOfToday, results.getFirst().getDataOverallUploaded(), results.getFirst().getDataOverallDownloaded());
        }
    }

    public TrafficJournalEntity updateData(String downloader, long overallDownloaded, long overallUploaded, long overallDownloadedProtocol, long overallUploadedProtocol) throws SQLException {
        long timestamp = MiscUtil.getStartOfHour(System.currentTimeMillis());
        TrafficJournalEntity journalEntity = queryBuilder()
                .where()
                .eq("downloader", downloader)
                .and()
                .eq("timestamp", timestamp)
                .queryForFirst();
        if (journalEntity == null) {
            journalEntity = new TrafficJournalEntity();
            journalEntity.setDownloader(downloader);
            journalEntity.setTimestamp(timestamp);
            journalEntity.setDataOverallDownloadedAtStart(overallDownloaded);
            journalEntity.setDataOverallUploadedAtStart(overallUploaded);
            journalEntity.setProtocolOverallDownloadedAtStart(overallDownloadedProtocol);
            journalEntity.setProtocolOverallUploadedAtStart(overallUploadedProtocol);
        }
        journalEntity.setDataOverallDownloaded(overallDownloaded);
        journalEntity.setDataOverallUploaded(overallUploaded);
        journalEntity.setProtocolOverallDownloaded(overallDownloadedProtocol);
        journalEntity.setProtocolOverallUploaded(overallUploadedProtocol);
        createOrUpdate(journalEntity);
        return journalEntity;
    }

    public List<TrafficDataComputed> getDayOffsetData(String downloader, Timestamp startAt, Timestamp endAt) throws Exception {
        List<TrafficDataComputed> results;
        if (downloader == null || downloader.isBlank()) {
            results = getAllDownloadersOverallData(startAt, endAt).stream().toList();
        } else {
            results = getSpecificDownloaderOverallData(downloader, startAt, endAt).stream().toList();
        }
        return results;
    }

    public List<TrafficDataComputed> getAllDownloadersOverallData(Timestamp start, Timestamp end) throws Exception {
        try (var results = queryBuilder().selectRaw(
                        "timestamp",
                        "SUM(dataOverallUploadedAtStart) AS totalUploadedAtStart",
                        "SUM(dataOverallUploaded) AS totalUploaded",
                        "SUM(dataOverallDownloadedAtStart) AS totalDownloadedAtStart",
                        "SUM(dataOverallDownloaded) AS totalDownloaded"
                )
                .where()
                .ge("timestamp", start.getTime())
                .and()
                .le("timestamp", end.getTime())
                .queryBuilder()
                .groupBy("timestamp")
                .queryRaw()) {
            return results.getResults().stream().map(args ->
                    new TrafficData(
                            new Timestamp(Long.parseLong(args[0])),
                            Long.parseLong(args[1]),
                            Long.parseLong(args[2]),
                            Long.parseLong(args[3]),
                            Long.parseLong(args[4])
                    )
            ).map(data -> new TrafficDataComputed(data.getTimestamp(),
                    data.getDataOverallUploaded() - data.getDataOverallUploadedAtStart(),
                    data.getDataOverallDownloaded() - data.getDataOverallDownloadedAtStart())).toList();
        }
    }

    public List<TrafficDataComputed> getSpecificDownloaderOverallData(String downloadName, Timestamp start, Timestamp end) throws SQLException {
        return queryBuilder().orderBy("timestamp", true)
                .where()
                .eq("downloader", downloadName)
                .and()
                .ge("timestamp", start.getTime())
                .and()
                .le("timestamp", end.getTime())
                .queryBuilder()
                .query().stream().map(e -> new TrafficData(
                        new Timestamp(e.getTimestamp()),
                        e.getDataOverallUploadedAtStart(),
                        e.getDataOverallUploaded(),
                        e.getDataOverallDownloadedAtStart(),
                        e.getDataOverallDownloaded()))
                .map(data -> new TrafficDataComputed(data.getTimestamp(),
                        data.getDataOverallUploaded() - data.getDataOverallUploadedAtStart(),
                        data.getDataOverallDownloaded() - data.getDataOverallDownloadedAtStart()))
                .toList();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrafficDataComputed {
        private Timestamp timestamp;
        private long dataOverallUploaded;
        private long dataOverallDownloaded;
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrafficData {
        private Timestamp timestamp;
        private long dataOverallUploadedAtStart;
        private long dataOverallUploaded;
        private long dataOverallDownloadedAtStart;
        private long dataOverallDownloaded;
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
