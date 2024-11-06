package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.HistoryEntity;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.ghostchu.peerbanhelper.util.paging.Page;
import com.ghostchu.peerbanhelper.util.paging.Pageable;
import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.stmt.SelectArg;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
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

    public Page<PeerBanCount> getBannedIps(Pageable pageable, String filter) throws Exception {
        var builder = queryBuilder()
                .selectRaw("ip, COUNT(*) AS count")
                .groupBy("ip")
                .orderByRaw("count DESC");
        if (filter != null) {
            builder.setWhere(builder.where().like("ip", new SelectArg(filter + "%")));
        }
        List<PeerBanCount> mapped;
        try (GenericRawResults<String[]> banLogs = builder
                .limit(pageable.getSize())
                .offset(pageable.getZeroBasedPage() * pageable.getSize())
                // .where().ge("banAt", twoWeeksAgo)
                .queryRaw()) {
            var results = banLogs.getResults();
            mapped = results.stream().map(arr -> new PeerBanCount(arr[0], Long.parseLong(arr[1]))).toList();
        }
        var countBuilder = queryBuilder()
                .selectColumns("ip");
        if (filter != null) {
            countBuilder.setWhere(countBuilder.where().like("ip", new SelectArg(filter + "%")));
        }
        return new Page<>(pageable, countBuilder.countOf("DISTINCT ip"), mapped);
    }

    public List<UniversalFieldNumResult> sumField(String field, double percentFilter, String downloader, Integer substringLength) throws Exception {
        // SQL 无 PreparedStatement 防注入；这绝对不是最佳实践，但在这个场景下足够用了
        if (!sqlSafePattern.matcher(field).matches()) {
            throw new IllegalArgumentException("Invalid field: " + field + ", only A-Z a-z 0-9 is allowed.");
        }
        List<UniversalFieldNumResult> results = new ArrayList<>();
        var sql = """
                SELECT
                                                 	%field% AS %fieldraw%,
                                                 	SUM( %field% ) AS ct,
                                                 	SUM( %field% ) * 1.0 / ( SELECT SUM( %field% ) FROM history WHERE downloader LIKE ? ) AS percent ,
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
                                                 	WHERE downloader LIKE ?\s
                                                 	)\s
                                                 GROUP BY
                                                 	%field%\s
                                                 HAVING
                                                 	percent > %percent%\s
                                                 ORDER BY
                                                 	ct DESC;
                """;
        sql = sql.replace("%percent%", String.valueOf(percentFilter))
                .replace("%fieldraw%", MsgUtil.escapeSql(field));
        if (substringLength != null) {
            sql = sql.replace("%field%", "SUBSTRING(" + MsgUtil.escapeSql(field) + ", 1, " + substringLength + ")");
        } else {
            sql = sql.replace("%field%", MsgUtil.escapeSql(field));
        }
        try (var resultSet = queryRaw(sql,
                downloader == null ? "%" : downloader,
                downloader == null ? "%" : downloader)) {
            for (String[] result : resultSet.getResults()) {
                results.add(new UniversalFieldNumResult(result[0], Long.parseLong(result[1]), Double.parseDouble(result[2])));
            }
        }
        return results;
    }

    public List<UniversalFieldNumResult> countField(String field, double percentFilter, String downloader, Integer substringLength) throws Exception {
        // SQL 无 PreparedStatement 防注入；这绝对不是最佳实践，但在这个场景下足够用了
        if (!sqlSafePattern.matcher(field).matches()) {
            throw new IllegalArgumentException("Invalid field: " + field + ", only A-Z a-z 0-9 is allowed.");
        }
        List<UniversalFieldNumResult> results = new ArrayList<>();
        var sql = """
                SELECT
                                                 	%field% AS %fieldraw%,
                                                 	COUNT( %field% ) AS ct,
                                                 	COUNT( %field% ) * 1.0 / ( SELECT COUNT( * ) FROM history WHERE downloader LIKE ? ) AS percent ,
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
                                                 	WHERE downloader LIKE ?\s
                                                 	)\s
                                                 GROUP BY
                                                 	%field%\s
                                                 HAVING
                                                 	percent > %percent%\s
                                                 ORDER BY
                                                 	ct DESC;
                """;

        sql = sql.replace("%percent%", String.valueOf(percentFilter))
                .replace("%fieldraw%", MsgUtil.escapeSql(field));
        if (substringLength != null) {
            sql = sql.replace("%field%", "SUBSTRING(" + MsgUtil.escapeSql(field) + ", 1, " + substringLength + ")");
        } else {
            sql = sql.replace("%field%", MsgUtil.escapeSql(field));
        }
        try (var resultSet = queryRaw(sql,
                downloader == null ? "%" : downloader,
                downloader == null ? "%" : downloader)) {
            for (String[] result : resultSet.getResults()) {
                results.add(new UniversalFieldNumResult(result[0], Long.parseLong(result[1]), Double.parseDouble(result[2])));
            }
        }
        return results;
    }

    public List<UniversalFieldDateResult> countDateField(long startAt, long endAt,
                                                         Function<HistoryEntity, Timestamp> timestampGetter,
                                                         Function<Calendar, Calendar> timestampTrimmer,
                                                         double percentFilter) throws Exception {
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