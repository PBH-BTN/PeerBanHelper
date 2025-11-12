package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.bittorrent.peer.PeerFlag;
import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.ghostchu.peerbanhelper.database.dao.impl.TrafficJournalDao;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.monitor.ActiveMonitoringModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.SimpleLongIntKVDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.SimpleStringIntKVDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.WebUtil;
import com.ghostchu.peerbanhelper.util.ipdb.IPDB;
import com.ghostchu.peerbanhelper.util.ipdb.IPDBManager;
import com.ghostchu.peerbanhelper.util.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.j256.ormlite.dao.RawRowMapper;
import com.j256.ormlite.stmt.SelectArg;
import io.javalin.http.Context;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@Slf4j
public final class PBHChartController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private PeerRecordDao peerRecordDao;
    @Autowired
    private HistoryDao historyDao;
    @Autowired
    private TrafficJournalDao trafficJournalDao;
    @Autowired
    private IPDBManager iPDBManager;
    @Autowired
    private ActiveMonitoringModule activeMonitoringModule;

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Charts";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-charts";
    }

    @Override
    public void onEnable() {
        webContainer.javalin()
                .get("/api/chart/geoIpInfo", this::handleGeoIP, Role.USER_READ, Role.PBH_PLUS)
                .get("/api/chart/trend", this::handlePeerTrends, Role.USER_READ, Role.PBH_PLUS)
                .get("/api/chart/traffic", this::handleTrafficClassic, Role.USER_READ, Role.PBH_PLUS)
                .get("/api/chart/sessionBetween", this::handleSessionBetween, Role.USER_READ, Role.PBH_PLUS)
                .get("/api/chart/sessionDayBucket", this::handleSessionDayBucket, Role.USER_READ, Role.PBH_PLUS)
        ;
    }

    private void handleSessionBetween(@NotNull Context ctx) throws SQLException {
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        String downloader = ctx.queryParam("downloader");
        if (downloader == null || downloader.isBlank()) {
            downloader = "%";
        }
        // 从 startAt 到 endAt，每天的开始时间戳
        var queryBuilder = peerRecordDao.queryBuilder();
        var where = queryBuilder
                .selectColumns("address")
                .distinct()
                .where();
        where.and(where.like("downloader", downloader), where.or(where.between("firstTimeSeen", timeQueryModel.startAt(), timeQueryModel.endAt()),
                where.between("lastTimeSeen", timeQueryModel.startAt(), timeQueryModel.endAt())));
        ctx.json(new StdResp(true, null, queryBuilder.countOf()));
    }

    private void handleSessionDayBucket(@NotNull Context ctx) throws Exception {
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        String downloader = ctx.queryParam("downloader");
        String downloaderParam = (downloader == null || downloader.isBlank()) ? null : downloader;
        long startAtTs = timeQueryModel.startAt().getTime();
        long endAtTs = timeQueryModel.endAt().getTime();
        Map<Long, SessionTimeRangeCounter> sessionDayBucket = new LinkedHashMap<>();
        String sql = """
                SELECT address, firstTimeSeen, lastTimeSeen, lastFlags FROM peer_records
                WHERE (? IS NULL OR downloader = ?) AND firstTimeSeen BETWEEN ? AND ?
                UNION
                SELECT address, firstTimeSeen, lastTimeSeen, lastFlags FROM peer_records
                WHERE (? IS NULL OR downloader = ?) AND lastTimeSeen BETWEEN ? AND ?
                """;
        String[] args = {
                downloaderParam, downloaderParam, String.valueOf(startAtTs), String.valueOf(endAtTs),
                downloaderParam, downloaderParam, String.valueOf(startAtTs), String.valueOf(endAtTs)
        };
        try (var results = peerRecordDao.queryRaw(sql, timeRangeMapper, args)) {
            for (SessionTimeRange row : results) {
                long firstDay = MiscUtil.getStartOfToday(row.firstTimeSeen);
                long lastDay = MiscUtil.getStartOfToday(row.lastTimeSeen);

                for (long day = firstDay; day <= lastDay; day += 86400000L) {
                    var counter = sessionDayBucket.computeIfAbsent(day, k -> new SessionTimeRangeCounter());
                    counter.total.incrementAndGet();
                    if (row.lastFlags != null && !row.lastFlags.isBlank() && !(new PeerFlag(row.lastFlags).isLocalConnection())) {
                        counter.incoming.incrementAndGet();
                    }
                }
            }
        }
        ctx.json(new StdResp(true, null, sessionDayBucket.entrySet().stream()
                .map(e -> new SessionDayBucketDTO(e.getKey(), e.getValue().total.intValue(), e.getValue().incoming.intValue()))
                .sorted(Comparator.comparingLong(SessionDayBucketDTO::key))
                .toList()));
    }

    public record SessionDayBucketDTO(long key, long total, long incoming) {

    }


    private static class SessionTimeRangeCounter {
        @Getter
        private final AtomicInteger total = new AtomicInteger(0);
        @Getter
        private final AtomicInteger incoming = new AtomicInteger(0);
    }

    private static class SessionTimeRange {
        private String address; // 虽然 Java 循环中不用，但 Mapper 需要接收
        private long firstTimeSeen;
        private long lastTimeSeen;
        private String lastFlags;
    }

    private final RawRowMapper<SessionTimeRange> timeRangeMapper =
            (columnNames, resultColumns) -> {
                SessionTimeRange range = new SessionTimeRange();
                range.address = resultColumns[0];
                range.firstTimeSeen = Long.parseLong(resultColumns[1]);
                range.lastTimeSeen = Long.parseLong(resultColumns[2]);
                range.lastFlags = resultColumns[3];
                return range;
            };

//    private void handleSessionDayBucket(@NotNull Context ctx) throws Exception {
//        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
//        String downloader = ctx.queryParam("downloader");
//        // 从 startAt 到 endAt，每天的开始时间戳
//        var queryBuilder = peerRecordDao.queryBuilder();
//        // 按天划分，每天的会话数量
//        var where = queryBuilder
//                .selectColumns("address", "firstTimeSeen", "lastTimeSeen")
//                .distinct()
//                .where();
//        var subwhere = downloader == null || downloader.isBlank() ? where.raw("1=1") : where.like("downloader", downloader);
//        where.and(subwhere, where.or(where.between("firstTimeSeen", timeQueryModel.startAt(), timeQueryModel.endAt()),
//                where.between("lastTimeSeen", timeQueryModel.startAt(), timeQueryModel.endAt())));
//        Map<Long, AtomicInteger> sessionDayBucket = new LinkedHashMap<>();
//        long startAt = System.currentTimeMillis();
//        try (var it = queryBuilder.iterator()) {
//            while (it.hasNext()) {
//                var record = it.next();
//                long firstDay = MiscUtil.getStartOfToday(record.getFirstTimeSeen().getTime());
//                long lastDay = MiscUtil.getStartOfToday(record.getLastTimeSeen().getTime());
//                for (long day = firstDay; day <= lastDay; day += 86400000L) {
//                    sessionDayBucket.computeIfAbsent(day, k -> new AtomicInteger()).incrementAndGet();
//                }
//            }
//            System.out.println("Iterator cost: "+ (System.currentTimeMillis() - startAt)+"ms");
//            ctx.json(new StdResp(true, null, sessionDayBucket.entrySet().stream()
//                    .map(e -> new SimpleLongIntKVDTO(e.getKey(), e.getValue().intValue()))
//                    .sorted(Comparator.comparingLong(SimpleLongIntKVDTO::key))
//                    .toList()));
//        }
//    }

    private void handleTraffic(Context ctx) throws Exception {
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        String downloader = ctx.queryParam("downloader");
        if (downloader == null || downloader.isBlank()) {
            ctx.json(new StdResp(true, null, fixTimezone(ctx, trafficJournalDao.getAllDownloadersOverallData(timeQueryModel.startAt(), timeQueryModel.endAt()))));
        } else {
            ctx.json(new StdResp(true, null, fixTimezone(ctx, trafficJournalDao.getSpecificDownloaderOverallData(downloader, timeQueryModel.startAt(), timeQueryModel.endAt()))));
        }
    }

//    private void handleTrafficClassic(Context ctx) throws Exception {
//        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
//        String downloader = ctx.queryParam("downloader");
//        var records = trafficJournalDao.getDayOffsetData(downloader,
//                timeQueryModel.startAt(),
//                timeQueryModel.endAt());
//        ctx.json(new StdResp(true, null, fixTimezone(ctx, records)));
//    }

    private void handleTrafficClassic(Context ctx) throws Exception {
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        String downloader = ctx.queryParam("downloader");
        var records = trafficJournalDao.getDayOffsetData(downloader,
                timeQueryModel.startAt(),
                timeQueryModel.endAt());

        // 将相同天的记录合并
        Map<Long, TrafficJournalDao.TrafficDataComputed> mergedData = new java.util.HashMap<>();

        for (TrafficJournalDao.TrafficDataComputed record : records) {
            // 获取当天的开始时间戳
            Timestamp ts = record.getTimestamp();
            long dayStart = MiscUtil.getStartOfToday(ts.getTime());

            // 合并相同日期的数据
            mergedData.compute(dayStart, (key, existing) -> {
                if (existing == null) {
                    return new TrafficJournalDao.TrafficDataComputed(
                            new Timestamp(key),
                            record.getDataOverallUploaded(),
                            record.getDataOverallDownloaded()
                    );
                } else {
                    existing.setDataOverallUploaded(existing.getDataOverallUploaded() + record.getDataOverallUploaded());
                    existing.setDataOverallDownloaded(existing.getDataOverallDownloaded() + record.getDataOverallDownloaded());
                    return existing;
                }
            });
        }

        // 转换回列表并排序
        List<TrafficJournalDao.TrafficDataComputed> mergedRecords = new ArrayList<>(mergedData.values());
        mergedRecords.sort(Comparator.comparing(data -> data.getTimestamp().getTime()));

        ctx.json(new StdResp(true, null, mergedRecords));
    }


    private TrafficJournalDao.TrafficDataComputed fixTimezone(Context ctx, TrafficJournalDao.TrafficDataComputed data) {
        Timestamp ts = data.getTimestamp();
        var epochSecond = ts.toLocalDateTime().atZone(timezone(ctx).toZoneId().getRules().getOffset(Instant.now()))
                .truncatedTo(ChronoUnit.DAYS).toEpochSecond();
        data.setTimestamp(new Timestamp(epochSecond * 1000));
        return data;
    }

    private List<TrafficJournalDao.TrafficDataComputed> fixTimezone(Context ctx, List<TrafficJournalDao.TrafficDataComputed> data) {
        data.forEach(d -> fixTimezone(ctx, d));
        return data;
    }

    private void handlePeerTrends(Context ctx) throws Exception {
        var downloader = ctx.queryParam("downloader");
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        Map<Long, AtomicInteger> connectedPeerTrends = new ConcurrentHashMap<>();
        Map<Long, AtomicInteger> bannedPeerTrends = new ConcurrentHashMap<>();
        var queryConnected = peerRecordDao.queryBuilder()
                .selectColumns("id", "lastTimeSeen")
                .where()
                .ge("lastTimeSeen", timeQueryModel.startAt())
                .and()
                .le("lastTimeSeen", timeQueryModel.endAt());
        var queryBanned = historyDao.queryBuilder()
                .selectColumns("id", "banAt")
                .where()
                .ge("banAt", timeQueryModel.startAt())
                .and()
                .le("banAt", timeQueryModel.endAt());
        if (downloader != null && !downloader.isBlank()) {
            queryConnected.and().eq("downloader", new SelectArg(downloader));
            queryBanned.and().eq("downloader", new SelectArg(downloader));
        }
        try (var it = queryConnected.iterator()) {
            while (it.hasNext()) {
                var startOfDay = MiscUtil.getStartOfToday(it.next().getLastTimeSeen().getTime());
                connectedPeerTrends.computeIfAbsent(startOfDay, k -> new AtomicInteger()).addAndGet(1);
            }
        }
        try (var it = queryBanned.iterator()) {
            while (it.hasNext()) {
                var startOfDay = MiscUtil.getStartOfToday(it.next().getBanAt().getTime());
                bannedPeerTrends.computeIfAbsent(startOfDay, k -> new AtomicInteger()).addAndGet(1);
            }
        }
        ctx.json(new StdResp(true, null, Map.of(
                "connectedPeersTrend", connectedPeerTrends.entrySet().stream()
                        .map((e) -> new SimpleLongIntKVDTO(e.getKey(), e.getValue().intValue()))
                        .sorted(Comparator.comparingLong(SimpleLongIntKVDTO::key))
                        .toList(),
                "bannedPeersTrend", bannedPeerTrends.entrySet().stream()
                        .map((e) -> new SimpleLongIntKVDTO(e.getKey(), e.getValue().intValue()))
                        .sorted(Comparator.comparingLong(SimpleLongIntKVDTO::key))
                        .toList()
        )));
    }

    private void handleGeoIP(Context ctx) throws Exception {
        IPDB ipdb = iPDBManager.getIpdb();
        if (ipdb == null) {
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.CHARTS_IPDB_NEED_INIT), null));
            return;
        }
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        var bannedOnly = Boolean.parseBoolean(ctx.queryParam("bannedOnly"));
        var downloader = ctx.queryParam("downloader");
        Map<String, AtomicInteger> ispCounter = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> cnProvinceCounter = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> cnCityCounter = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> countryOrRegionCounter = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> netTypeCounter = new ConcurrentHashMap<>();
        var queryBanned = historyDao.queryBuilder()
                .distinct()
                .selectColumns("id", "ip")
                .where()
                .ge("banAt", timeQueryModel.startAt())
                .and()
                .le("banAt", timeQueryModel.endAt());
        var queryConnected = peerRecordDao.queryBuilder()
                .distinct()
                .selectColumns("id", "address")
                .where()
                .ge("lastTimeSeen", timeQueryModel.startAt())
                .and()
                .le("lastTimeSeen", timeQueryModel.endAt());
        if (downloader != null && !downloader.isBlank()) {
            queryBanned.and().eq("downloader", new SelectArg(downloader));
            queryConnected.and().eq("downloader", new SelectArg(downloader));
        }
        try (var itBanned = queryBanned.iterator();
             var itConnected = queryConnected.iterator()) {
            try (ExecutorService service = Executors.newWorkStealingPool()) {
                var ipIterator = new Iterator<String>() {
                    @Override
                    public boolean hasNext() {
                        return bannedOnly ? itBanned.hasNext() : itConnected.hasNext();
                    }

                    @Override
                    public String next() {
                        return bannedOnly ? itBanned.next().getIp() : itConnected.next().getAddress();
                    }
                };
                while (ipIterator.hasNext()) {
                    var ip = ipIterator.next();
                    service.submit(() -> {
                        try {
                            String determindIp = ip;
                            if (IPAddressUtil.getIPAddress(determindIp).isPrefixed()) {
                                determindIp = IPAddressUtil.getIPAddress(determindIp).toPrefixBlock().getLower().withoutPrefixLength().toNormalizedString();
                            }
                            IPGeoData ipGeoData = ipdb.query(InetAddress.getByName(determindIp));
                            String isp = "N/A";
                            if (ipGeoData.getAs() != null) {
                                isp = ipGeoData.getAs().getOrganization();
                            }
                            String countryOrRegion = "N/A";
                            String province = "N/A";
                            String city = "N/A";
                            String netType = "N/A";
                            if (ipGeoData.getCountry() != null) {
                                countryOrRegion = ipGeoData.getCountry().getName();
                            }
                            if (ipGeoData.getCity() != null) {
                                city = ipGeoData.getCity().getName();
                                if (ipGeoData.getCity().getCnProvince() != null) {
                                    province = ipGeoData.getCity().getCnProvince();
                                }
                                if (ipGeoData.getCity().getCnCity() != null) {
                                    city = ipGeoData.getCity().getCnProvince() + " " + ipGeoData.getCity().getCnCity();
                                }
                            }
                            if (ipGeoData.getNetwork() != null) {
                                isp = ipGeoData.getNetwork().getIsp();
                                netType = ipGeoData.getNetwork().getNetType();
                            }
                            ispCounter.computeIfAbsent(isp, k -> new AtomicInteger()).incrementAndGet();
                            cnProvinceCounter.computeIfAbsent(province, k -> new AtomicInteger()).incrementAndGet();
                            cnCityCounter.computeIfAbsent(city, k -> new AtomicInteger()).incrementAndGet();
                            countryOrRegionCounter.computeIfAbsent(countryOrRegion, k -> new AtomicInteger()).incrementAndGet();
                            netTypeCounter.computeIfAbsent(netType, k -> new AtomicInteger()).incrementAndGet();
                        } catch (UnknownHostException e) {
                            log.error("Unable to resolve the GeoIP data for ip {}", ip, e);
                        }
                    });
                }
            }
        }
        ctx.json(new StdResp(true, null, Map.of(
                "isp", ispCounter.entrySet().stream().map((e) -> new SimpleStringIntKVDTO(e.getKey(), e.getValue().intValue()))
                        .sorted((o1, o2) -> Integer.compare(o2.value(), o1.value()))
                        .toList(),
                "province", cnProvinceCounter.entrySet().stream().map((e) -> new SimpleStringIntKVDTO(e.getKey(), e.getValue().intValue()))
                        .sorted((o1, o2) -> Integer.compare(o2.value(), o1.value()))
                        .toList(),
                "region", countryOrRegionCounter.entrySet().stream().map((e) -> new SimpleStringIntKVDTO(e.getKey(), e.getValue().intValue()))
                        .sorted((o1, o2) -> Integer.compare(o2.value(), o1.value()))
                        .toList(),
                "city", cnCityCounter.entrySet().stream().map((e) -> new SimpleStringIntKVDTO(e.getKey(), e.getValue().intValue()))
                        .sorted((o1, o2) -> Integer.compare(o2.value(), o1.value()))
                        .toList()
        )));
    }

    @Override
    public void onDisable() {

    }

    record SimpleLongLongKV(long key, long value) {

    }

    record TrafficJournalRecord(
            long timestamp,
            long uploaded,
            long downloaded
    ) {

    }

//    public record GeoIPQuery(GeoIPPie data, long count) {
//
//    }
//
//    public record GeoIPPie(
//            String country,
//            String province,
//            String city,
//            String districts,
//            String net,
//            String isp
//    ) {
//
//    }
}
