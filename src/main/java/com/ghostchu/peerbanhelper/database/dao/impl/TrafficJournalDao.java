package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.TrafficJournalEntity;
import com.ghostchu.peerbanhelper.lab.Experiments;
import com.ghostchu.peerbanhelper.lab.Laboratory;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.j256.ormlite.stmt.SelectArg;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
public final class TrafficJournalDao extends AbstractPBHDao<TrafficJournalEntity, Long> {
    private final Laboratory laboratory;

    public TrafficJournalDao(@Autowired Database database, @Autowired Laboratory laboratory) throws SQLException {
        super(database.getDataSource(), TrafficJournalEntity.class);
        this.laboratory = laboratory;
    }

    public TrafficJournalEntity getTodayJournal(String downloader) throws SQLException {
        return createIfNotExists(new TrafficJournalEntity(null, MiscUtil.getStartOfToday(System.currentTimeMillis()), downloader, 0, 0, 0, 0));
    }

    public List<TrafficJournalDao.TrafficData> getDayOffsetData(String downloader, Timestamp startAt, Timestamp endAt, Consumer<TrafficJournalDao.TrafficData> fixTimeZone) throws Exception {
        List<TrafficJournalDao.TrafficData> results;
        if (downloader == null || downloader.isBlank()) {
            results = getAllDownloadersOverallData(startAt, endAt).stream().peek(this::fixTimezone).toList();
        } else {
            results = getSpecificDownloaderOverallData(downloader, startAt, endAt).stream().peek(this::fixTimezone).toList();
        }

        List<TrafficJournalDao.TrafficData> records = new ArrayList<>();
        var it = results.iterator();
        // -----
        TrafficJournalDao.TrafficData base = null;
        while (it.hasNext()) {/**/
            var target = it.next();

            if (base == null) {
                base = target;
                continue; // 跳过插入当天的总数据，直接计算增量
            }

            if (laboratory.isExperimentActivated(Experiments.FILL_MISSING_DATA_IN_TRAFFIC_SUMMARY.getExperiment())) {
                // 计算时间差
                long timeDifference = target.getTimestamp().getTime() - base.getTimestamp().getTime();

                // 如果时间差大于一天，说明有跳过的日期，使用上一条数据填充中间跳过的所有天
                if (timeDifference > 86400000) {
                    long days = timeDifference / 86400000;
                    for (long i = 1; i < days; i++) {
                        var newTimestamp = new Timestamp(base.getTimestamp().getTime() + 86400000 * i);
                        var newRecord = new TrafficJournalDao.TrafficData(newTimestamp, base.getDataOverallUploaded(), base.getDataOverallDownloaded());
                        fixTimeZone.accept(newRecord);
                        records.add(newRecord);
                    }
                }
            }

            long uploadedOffset = target.getDataOverallUploaded() - base.getDataOverallUploaded();
            long downloadedOffset = target.getDataOverallDownloaded() - base.getDataOverallDownloaded();
            if (uploadedOffset < 0) uploadedOffset = 0;
            if (downloadedOffset < 0) downloadedOffset = 0;

            // 插入增量数据
            records.add(new TrafficJournalDao.TrafficData(target.getTimestamp(), uploadedOffset, downloadedOffset));
            base = target;
        }
        return records;
    }


    public void fixTimezone(TrafficJournalDao.TrafficData data) {
        Timestamp ts = data.getTimestamp();
        var epochSecond = ts.toLocalDateTime().atZone(MiscUtil.getSystemZoneOffset().getRules().getOffset(Instant.now()))
                .truncatedTo(ChronoUnit.DAYS).toEpochSecond();
        data.setTimestamp(new Timestamp(epochSecond * 1000));
    }


    public List<TrafficData> getAllDownloadersOverallData(Timestamp start, Timestamp end) throws Exception {
        try (var results = queryBuilder().selectRaw("timestamp", "SUM(dataOverallUploaded) AS totalUploaded", "SUM(dataOverallDownloaded) AS totalDownloaded")
                .where()
                .ge("timestamp", start.getTime())
                .and()
                .le("timestamp", end.getTime())
                .queryBuilder()
                .groupBy("timestamp")
                .queryRaw()) {
            return results.getResults().stream().map(args -> new TrafficData(new Timestamp(Long.parseLong(args[0])), Long.parseLong(args[1]), Long.parseLong(args[2]))).toList();
        }
    }

    public List<TrafficData> getSpecificDownloaderOverallData(String downloadName, Timestamp start, Timestamp end) throws Exception {
        try (var results = queryBuilder().selectRaw("timestamp", "SUM(dataOverallUploaded) AS totalUploaded", "SUM(dataOverallDownloaded) AS totalDownloaded")
                .where()
                .ge("timestamp", start.getTime())
                .and()
                .le("timestamp", end.getTime())
                .and()
                .eq("downloader", MsgUtil.escapeSql(downloadName))
                .queryBuilder()
                .groupBy("timestamp")
                .queryRaw()) {
            return results.getResults().stream().map(args -> new TrafficData(new Timestamp(Long.parseLong(args[0])), Long.parseLong(args[1]), Long.parseLong(args[2]))).toList();
        }
    }

    @Override
    public synchronized TrafficJournalEntity createIfNotExists(TrafficJournalEntity data) throws SQLException {
        if (data == null) {
            return null;
        }
        TrafficJournalEntity existing = queryBuilder().where()
                .eq("timestamp", data.getTimestamp())
                .and()
                .eq("downloader", new SelectArg(data.getDownloader())).queryBuilder().queryForFirst();
        if (existing == null) {
            create(data);
            return data;
        } else {
            return existing;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrafficData {
        private Timestamp timestamp;
        private long dataOverallUploaded;
        private long dataOverallDownloaded;
    }

}
