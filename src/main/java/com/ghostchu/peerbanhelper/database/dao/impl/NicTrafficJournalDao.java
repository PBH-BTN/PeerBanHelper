package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.NicTrafficJournalEntity;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.j256.ormlite.support.ConnectionSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public final class NicTrafficJournalDao extends AbstractPBHDao<NicTrafficJournalEntity, Long> {

    public NicTrafficJournalDao(@Autowired ConnectionSource database) throws SQLException {
        super(database, NicTrafficJournalEntity.class);
    }

    public TrafficDataComputed getTodayData(String nic) throws Exception {
        Timestamp startOfToday = new Timestamp(MiscUtil.getStartOfToday(System.currentTimeMillis()));
        Timestamp endOfToday = new Timestamp(MiscUtil.getEndOfToday(System.currentTimeMillis()));
        List<TrafficDataComputed> results;
        if (nic == null || nic.isBlank()) {
            results = getAllNicOverallData(startOfToday, endOfToday).stream().toList();
        } else {
            results = getSpecificNicOverallData(nic, startOfToday, endOfToday).stream().toList();
        }
        if (results.isEmpty()) {
            return new TrafficDataComputed(startOfToday, 0, 0, 0, 0);
        } else {
            return new TrafficDataComputed(
                    startOfToday,
                    results.stream().mapToLong(TrafficDataComputed::getBytesReceived).sum(),
                    results.stream().mapToLong(TrafficDataComputed::getBytesSent).sum(),
                    results.stream().mapToLong(TrafficDataComputed::getPacketsReceived).sum(),
                    results.stream().mapToLong(TrafficDataComputed::getPacketsSent).sum()
            );
        }
    }

    public List<String> listNics() throws Exception {
        try (var raw = queryBuilder().selectColumns("nic").distinct().queryRaw()) {
            List<String> nics = new ArrayList<>();
            for (String[] strings : raw) {
                nics.add(strings[0]);
            }
            return nics;
        }
    }

    public NicTrafficJournalEntity updateData(String nic, long bytesReceived, long bytesSent, long packetsReceived, long packetsSent) throws SQLException {
        long timestamp = MiscUtil.getStartOfHour(System.currentTimeMillis());
        NicTrafficJournalEntity journalEntity = queryBuilder()
                .where()
                .eq("nic", nic)
                .and()
                .eq("timestamp", timestamp)
                .queryForFirst();
        if (journalEntity == null) {
            journalEntity = new NicTrafficJournalEntity();
            journalEntity.setNic(nic);
            journalEntity.setTimestamp(timestamp);
            journalEntity.setBytesReceivedAtStart(bytesReceived);
            journalEntity.setBytesSentAtStart(bytesSent);
            journalEntity.setPacketsReceivedAtStart(packetsReceived);
            journalEntity.setPacketsSentAtStart(packetsReceived);
        }
        journalEntity.setBytesReceived(bytesReceived);
        journalEntity.setBytesSent(bytesSent);
        journalEntity.setPacketsReceived(packetsReceived);
        journalEntity.setPacketsSent(packetsReceived);
        createOrUpdate(journalEntity);
        return journalEntity;
    }

    public List<TrafficDataComputed> getDayOffsetData(String nic, Timestamp startAt, Timestamp endAt) throws Exception {
        List<TrafficDataComputed> results;
        if (nic == null || nic.isBlank()) {
            results = getAllNicOverallData(startAt, endAt).stream().toList();
        } else {
            results = getSpecificNicOverallData(nic, startAt, endAt).stream().toList();
        }
        return results;
    }

    public List<TrafficDataComputed> getAllNicOverallData(Timestamp start, Timestamp end) throws Exception {
        try (var results = queryBuilder().selectRaw(
                        "timestamp",
                        "SUM(bytesReceivedAtStart) AS totalBytesReceivedAtStart",
                        "SUM(bytesReceived) AS totalBytesReceived",
                        "SUM(bytesSentAtStart) AS totalBytesSentAtStart",
                        "SUM(bytesSent) AS totalBytesSent",
                        "SUM(packetsReceivedAtStart) AS totalPacketsReceivedAtStart",
                        "SUM(packetsReceived) AS totalPacketsReceived",
                        "SUM(packetsSentAtStart) AS totalPacketsSentAtStart",
                        "SUM(packetsSent) AS totalPacketsSent"
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
                            Long.parseLong(args[4]),
                            Long.parseLong(args[5]),
                            Long.parseLong(args[6]),
                            Long.parseLong(args[7]),
                            Long.parseLong(args[8]))
            ).map(data -> new TrafficDataComputed(data.getTimestamp(),
                    data.getBytesReceived() - data.getBytesReceivedAtStart(),
                    data.getBytesSent() - data.getBytesSentAtStart(),
                    data.getPacketsReceived() - data.getPacketsReceivedAtStart(),
                    data.getPacketsSent() - data.getPacketsSentAtStart()
            )).toList();
        }
    }

    public List<TrafficDataComputed> getSpecificNicOverallData(String nic, Timestamp start, Timestamp end) throws SQLException {
        return queryBuilder().orderBy("timestamp", true)
                .where()
                .eq("nic", nic)
                .and()
                .ge("timestamp", start.getTime())
                .and()
                .le("timestamp", end.getTime())
                .queryBuilder()
                .query().stream().map(e -> new TrafficData(
                        new Timestamp(e.getTimestamp()),
                        e.getBytesReceivedAtStart(),
                        e.getBytesReceived(),
                        e.getBytesSentAtStart(),
                        e.getBytesSent(),
                        e.getPacketsReceivedAtStart(),
                        e.getPacketsReceived(),
                        e.getPacketsSentAtStart(),
                        e.getPacketsSent()))
                .map(data -> new TrafficDataComputed(data.getTimestamp(),
                        data.getBytesReceived() - data.getBytesReceivedAtStart(),
                        data.getBytesSent() - data.getBytesSentAtStart(),
                        data.getPacketsReceived() - data.getPacketsReceivedAtStart(),
                        data.getPacketsSent() - data.getPacketsSentAtStart()))
                .toList();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrafficDataComputed {
        private Timestamp timestamp;
        private long bytesReceived;
        private long bytesSent;
        private long packetsReceived;
        private long packetsSent;
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrafficData {
        private Timestamp timestamp;
        private long bytesReceivedAtStart;
        private long bytesReceived;
        private long bytesSentAtStart;
        private long bytesSent;
        private long packetsReceivedAtStart;
        private long packetsReceived;
        private long packetsSentAtStart;
        private long packetsSent;
    }

}
