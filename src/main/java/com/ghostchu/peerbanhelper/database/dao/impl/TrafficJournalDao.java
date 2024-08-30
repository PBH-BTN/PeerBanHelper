package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.TrafficJournalEntity;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Component
public class TrafficJournalDao extends AbstractPBHDao<TrafficJournalEntity, Long> {
    public TrafficJournalDao(@Autowired Database database) throws SQLException {
        super(database.getDataSource(), TrafficJournalEntity.class);
    }

    public TrafficJournalEntity getTodayJournal(String downloader) throws SQLException {
        return createIfNotExists(new TrafficJournalEntity(null, MiscUtil.getStartOfToday(System.currentTimeMillis()), downloader, 0, 0, 0, 0));
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
                .eq("downloader", downloadName)
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
                .eq("downloader", data.getDownloader()).queryBuilder().queryForFirst();
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
