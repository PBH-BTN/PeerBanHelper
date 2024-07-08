package com.ghostchu.peerbanhelper.database.dao;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.table.HistoryEntity;
import com.j256.ormlite.dao.BaseDaoImpl;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class HistoryDao extends BaseDaoImpl<HistoryEntity, Long> {
    public HistoryDao(@Autowired Database database) throws SQLException {
        super(database.getDataSource(), HistoryEntity.class);
    }

    @Override
    public synchronized HistoryEntity createIfNotExists(HistoryEntity data) throws SQLException {
        List<HistoryEntity> list = queryForMatchingArgs(data);
        if (list.isEmpty()) {
            return super.createIfNotExists(data);
        }
        return list.getFirst();
    }

    public Map<String, Long> getBannedIps(int n) throws Exception {
        Timestamp twoWeeksAgo = new Timestamp(Instant.now().minus(14, ChronoUnit.DAYS).toEpochMilli());

        String sql = "SELECT ip, COUNT(*) AS count FROM " + getTableName() + " WHERE banAt >= ? " +
                "GROUP BY ip ORDER BY count DESC LIMIT " + n;

        Map<String, Long> result = new HashMap<>();
        var banLogs = super.queryRaw(sql, twoWeeksAgo.toString());
        try (banLogs) {
            var results = banLogs.getResults();
            results.forEach(arr -> result.put(arr[0], Long.parseLong(arr[1])));
        }
        return result;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class PeerBanCount {
        private String peerIp;
        private long count;
    }
}
