package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.HistoryEntity;
import com.j256.ormlite.dao.GenericRawResults;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.regex.Pattern;

@Component
public class HistoryDao extends AbstractPBHDao<HistoryEntity, Long> {
    private final Pattern sqlSafePattern;

    public HistoryDao(@Autowired Database database) throws SQLException {
        super(database.getDataSource(), HistoryEntity.class);
        this.sqlSafePattern = Pattern.compile("^[A-Za-z0-9]+$");
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
        Timestamp twoWeeksAgo = new Timestamp(Instant.now().minus(60, ChronoUnit.DAYS).toEpochMilli());
        Map<String, Long> result = new HashMap<>();
        try (GenericRawResults<String[]> banLogs = queryBuilder()
                .selectRaw("ip, COUNT(*) AS count")
                .groupBy("ip")
                .orderBy("count", false)
                .limit((long) n)
                .queryRaw()) {
            var results = banLogs.getResults();
            results.forEach(arr -> result.put(arr[0], Long.parseLong(arr[1])));
        }
        return result;
    }

    public List<UniversalFieldNumResult> sumField(String field, double percentFilter) throws Exception {
        // SQL 无 PreparedStatement 防注入；这绝对不是最佳实践，但在这个场景下足够用了
        if (!sqlSafePattern.matcher(field).matches()) {
            throw new IllegalArgumentException("Invalid field: " + field + ", only A-Z a-z 0-9 is allowed.");
        }
        List<UniversalFieldNumResult> results = new ArrayList<>();
        var sql = """
                SELECT
                                                 	%field%,
                                                 	SUM( %field% ) AS ct,
                                                 	SUM( %field% ) * 1.0 / ( SELECT SUM( %field% ) FROM history ) AS percent ,
                                                 	torrentName,
                                                 	torrentInfoHash,
                                                 	module
                                                 FROM
                                                 	(
                                                 	SELECT
                                                 		*,
                                                 		torrents.infoHash AS torrentInfoHash,
                                                 		torrents.name AS torrentName,
                                                 		modules.name AS module\s
                                                 	FROM
                                                 		(
                                                 			( ( history INNER JOIN torrents ON history.torrent_id = torrents.id ) INNER JOIN rules ON history.rule_id = rules.id )\s
                                                 		)
                                                 		INNER JOIN modules ON modules.id = rules.module_id\s
                                                 	)\s
                                                 GROUP BY
                                                 	%field%\s
                                                 HAVING
                                                 	percent > %percent%\s
                                                 ORDER BY
                                                 	ct DESC;
                """;
        sql = sql.replace("%field%", field)
                .replace("%percent%", String.valueOf(percentFilter));
        try (var resultSet = queryRaw(sql)) {
            for (String[] result : resultSet.getResults()) {
                results.add(new UniversalFieldNumResult(result[0], Long.parseLong(result[1]), Double.parseDouble(result[2])));
            }
        }
        return results;
    }

    public List<UniversalFieldNumResult> countField(String field, double percentFilter) throws Exception {
        // SQL 无 PreparedStatement 防注入；这绝对不是最佳实践，但在这个场景下足够用了
        if (!sqlSafePattern.matcher(field).matches()) {
            throw new IllegalArgumentException("Invalid field: " + field + ", only A-Z a-z 0-9 is allowed.");
        }
        List<UniversalFieldNumResult> results = new ArrayList<>();
        var sql = """
                SELECT
                                                 	%field%,
                                                 	COUNT( %field% ) AS ct,
                                                 	COUNT( %field% ) * 1.0 / ( SELECT COUNT( * ) FROM history ) AS percent ,
                                                 	torrentName,
                                                 	torrentInfoHash,
                                                 	module
                                                 FROM
                                                 	(
                                                 	SELECT
                                                 		*,
                                                 		torrents.infoHash AS torrentInfoHash,
                                                 		torrents.name AS torrentName,
                                                 		modules.name AS module\s
                                                 	FROM
                                                 		(
                                                 			( ( history INNER JOIN torrents ON history.torrent_id = torrents.id ) INNER JOIN rules ON history.rule_id = rules.id )\s
                                                 		)
                                                 		INNER JOIN modules ON modules.id = rules.module_id\s
                                                 	)\s
                                                 GROUP BY
                                                 	%field%\s
                                                 HAVING
                                                 	percent > %percent%\s
                                                 ORDER BY
                                                 	ct DESC;
                """;

        sql = sql.replace("%field%", field)
                .replace("%percent%", String.valueOf(percentFilter));
        try (var resultSet = queryRaw(sql)) {
            for (String[] result : resultSet.getResults()) {
                results.add(new UniversalFieldNumResult(result[0], Long.parseLong(result[1]), Double.parseDouble(result[2])));
            }
        }
        return results;
    }

    public List<UniversalFieldDateResult> countDateField(long startAt, long endAt, Function<HistoryEntity, Timestamp> timestampGetter, Function<Calendar, Calendar> timestampTrimmer, double percentFilter) throws Exception {
        Map<Long, AtomicLong> counterMap = new HashMap<>();
        try (var it = iterator()) {
            while (it.hasNext()) {
                var row = it.next();
                Timestamp field = timestampGetter.apply(row);
                long fieldT = field.getTime();
                if (!(fieldT >= startAt && fieldT <= endAt)) {
                    continue;
                }
                Calendar fuckCal = Calendar.getInstance();
                fuckCal.setTime(field);
                Calendar trimmed = timestampTrimmer.apply(fuckCal);
                long time = trimmed.getTime().getTime();
                AtomicLong atomicLong = counterMap.getOrDefault(time, new AtomicLong(0));
                atomicLong.incrementAndGet();
                counterMap.put(time, atomicLong);
            }
        }
        // 计算总量
        long total = counterMap.values().stream().mapToLong(AtomicLong::get).sum();
        List<UniversalFieldDateResult> results = new ArrayList<>();
        for (Map.Entry<Long, AtomicLong> dateMappingAtomicLongEntry : counterMap.entrySet()) {
            results.add(new UniversalFieldDateResult(dateMappingAtomicLongEntry.getKey(),
                    dateMappingAtomicLongEntry.getValue().get(),
                    (double) dateMappingAtomicLongEntry.getValue().get() / total
            ));
        }
        results.removeIf(r -> r.percent() < percentFilter);
        return results;
    }


    public record UniversalFieldNumResult(String data, long count, double percent) {

    }

    public record UniversalFieldDateResult(long timestamp, long count,
                                           double percent) {

    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class PeerBanCount {
        private String peerIp;
        private long count;
    }
}
