package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ghostchu.peerbanhelper.bittorrent.peer.PeerFlag;
import com.ghostchu.peerbanhelper.databasent.dto.TrafficDataComputed;
import com.ghostchu.peerbanhelper.databasent.service.HistoryService;
import com.ghostchu.peerbanhelper.databasent.service.PeerConnectionMetricsService;
import com.ghostchu.peerbanhelper.databasent.service.PeerRecordService;
import com.ghostchu.peerbanhelper.databasent.service.impl.common.TrafficJournalServiceImpl;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.databasent.table.PeerRecordEntity;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.SimpleLongIntKVDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.SimpleStringIntKVDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.WebUtil;
import com.ghostchu.peerbanhelper.util.ipdb.IPDB;
import com.ghostchu.peerbanhelper.util.ipdb.IPDBManager;
import com.ghostchu.peerbanhelper.util.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import com.ghostchu.peerbanhelper.util.query.PBHPage;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.OffsetDateTime;
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
    private PeerRecordService peerRecordService;
    @Autowired
    private HistoryService historyService;
    @Autowired
    private TrafficJournalServiceImpl trafficJournalDao;
    @Autowired
    private IPDBManager iPDBManager;
    @Autowired
    private PeerConnectionMetricsService peerConnectionMetricDao;

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
                .get("/api/chart/sessionAnalyse", this::handleSessionAnalyse, Role.USER_READ, Role.PBH_PLUS)
                .get("/api/chart/clientAnalyse", this::handleClientAnalyse, Role.USER_READ, Role.PBH_PLUS)
        ;
    }

    private void handleClientAnalyse(@NotNull Context ctx) {
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        var downloader = ctx.queryParam("downloader");
        Pageable pageable = new Pageable(ctx);
        Orderable orderable = new Orderable(Map.of("uploaded", false), ctx);
        var dtoPage = peerRecordService.queryClientAnalyse(pageable.toPage(), timeQueryModel.startAt(), timeQueryModel.endAt(), downloader, orderable.generateOrderBy());
        ctx.json(new StdResp(true, null, PBHPage.from(dtoPage)));
    }


    private void handleSessionAnalyse(@NotNull Context ctx) {
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        var downloader = ctx.queryParam("downloader");
        var data = peerConnectionMetricDao.getMetricsSince(timeQueryModel.startAt(), timeQueryModel.endAt(), downloader);
        ctx.json(new StdResp(true, null, data));
    }

    private void handleSessionBetween(@NotNull Context ctx) {
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        String downloader = ctx.queryParam("downloader");
        if (downloader == null || downloader.isBlank()) {
            downloader = "%";
        }
        // 从 startAt 到 endAt，每天的开始时间戳
        ctx.json(new StdResp(true, null, peerRecordService.sessionBetween(downloader, timeQueryModel.startAt(), timeQueryModel.endAt())));
    }

    private void handleSessionDayBucket(@NotNull Context ctx) {
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        String downloader = ctx.queryParam("downloader");
        long startAtTs = timeQueryModel.startAt().toInstant().toEpochMilli();
        long endAtTs = timeQueryModel.endAt().toInstant().toEpochMilli();
        Map<Long, SessionTimeRangeCounter> sessionDayBucket = new LinkedHashMap<>();

        LambdaQueryWrapper<PeerRecordEntity> query = Wrappers.<PeerRecordEntity>lambdaQuery()
                .ge(PeerRecordEntity::getFirstTimeSeen, timeQueryModel.startAt())
                .le(PeerRecordEntity::getLastTimeSeen, timeQueryModel.endAt());

        if (downloader != null) {
            query.eq(PeerRecordEntity::getDownloader, downloader);
        }

        List<PeerRecordEntity> peerRecords = peerRecordService.list(query);
        // 生成按日时间戳的桶，并填充数据
        for (PeerRecordEntity record : peerRecords) {
            long firstDay = MiscUtil.getStartOfToday(record.getFirstTimeSeen().toInstant().toEpochMilli()).toInstant().toEpochMilli();
            long lastDay = MiscUtil.getStartOfToday(record.getLastTimeSeen().toInstant().toEpochMilli()).toInstant().toEpochMilli();
            for (long day = firstDay; day <= lastDay; day += 86400000L) {
                if (day < startAtTs || day > endAtTs) {
                    continue;
                }
                SessionTimeRangeCounter counter = sessionDayBucket.computeIfAbsent(day, k -> new SessionTimeRangeCounter());
                counter.getTotal().incrementAndGet();
                if (StringUtils.isNotBlank(record.getLastFlags()) && !new PeerFlag(record.getLastFlags()).isLocalConnection()) {
                    counter.getIncoming().incrementAndGet();
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

    private void handleTrafficClassic(Context ctx) {
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        String downloader = ctx.queryParam("downloader");
        var records = trafficJournalDao.getDayOffsetData(downloader,
                timeQueryModel.startAt(),
                timeQueryModel.endAt());

        // 按天分组,累加每天的流量数据（TrafficDataComputed中的值已经是增量，需要求和）
        Map<Long, TrafficDataComputed> mergedData = new java.util.HashMap<>();

        for (TrafficDataComputed record : records) {
            OffsetDateTime ts = record.getTimestamp();
            long dayStart = MiscUtil.getStartOfToday(ts.toInstant().toEpochMilli()).toInstant().toEpochMilli();

            mergedData.compute(dayStart, (key, existing) -> {
                if (existing == null) {
                    return new TrafficDataComputed(
                            Instant.ofEpochMilli(key).atZone(ts.getOffset()).toOffsetDateTime(),
                            record.getDataOverallUploaded(),
                            record.getDataOverallDownloaded()
                    );
                } else {
                    // 累加每小时的流量增量
                    existing.setDataOverallUploaded(existing.getDataOverallUploaded() + record.getDataOverallUploaded());
                    existing.setDataOverallDownloaded(existing.getDataOverallDownloaded() + record.getDataOverallDownloaded());
                    return existing;
                }
            });
        }

        List<TrafficDataComputed> mergedRecords = new ArrayList<>(mergedData.values());
        mergedRecords.sort(Comparator.comparing(TrafficDataComputed::getTimestamp));

        ctx.json(new StdResp(true, null, mergedRecords));
    }

    private void handlePeerTrends(Context ctx) {
        var downloader = ctx.queryParam("downloader");
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        Map<Long, AtomicInteger> connectedPeerTrends = new ConcurrentHashMap<>();
        Map<Long, AtomicInteger> bannedPeerTrends = new ConcurrentHashMap<>();

        var queryConnected = Wrappers.<PeerRecordEntity>lambdaQuery()
                .select(PeerRecordEntity::getId, PeerRecordEntity::getLastTimeSeen)
                .ge(PeerRecordEntity::getLastTimeSeen, timeQueryModel.startAt())
                .le(PeerRecordEntity::getLastTimeSeen, timeQueryModel.endAt());
        var queryBanned = Wrappers.<HistoryEntity>lambdaQuery()
                .select(HistoryEntity::getId, HistoryEntity::getBanAt)
                .ge(HistoryEntity::getBanAt, timeQueryModel.startAt())
                .le(HistoryEntity::getBanAt, timeQueryModel.endAt());

        if (downloader != null && !downloader.isBlank()) {
            queryConnected.eq(PeerRecordEntity::getDownloader, downloader);
            queryBanned.eq(HistoryEntity::getDownloader, downloader);
        }

        peerRecordService.list(queryConnected).forEach(entity -> {
            var startOfDay = MiscUtil.getStartOfToday(entity.getLastTimeSeen().toInstant().toEpochMilli());
            connectedPeerTrends.computeIfAbsent(startOfDay.toInstant().toEpochMilli(), k -> new AtomicInteger()).addAndGet(1);
        });

        historyService.list(queryBanned).forEach(entity -> {
            var startOfDay = MiscUtil.getStartOfToday(entity.getBanAt().toInstant().toEpochMilli());
            bannedPeerTrends.computeIfAbsent(startOfDay.toInstant().toEpochMilli(), k -> new AtomicInteger()).addAndGet(1);
        });

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

    private void handleGeoIP(Context ctx) {
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

        var queryBanned = Wrappers.<HistoryEntity>lambdaQuery()
                .select(HistoryEntity::getId, HistoryEntity::getIp) // distinct not supported directly in wrapper chain easily without custom SQL or loading all
                .ge(HistoryEntity::getBanAt, timeQueryModel.startAt())
                .le(HistoryEntity::getBanAt, timeQueryModel.endAt());
        var queryConnected = Wrappers.<PeerRecordEntity>lambdaQuery()
                .select(PeerRecordEntity::getId, PeerRecordEntity::getAddress)
                .ge(PeerRecordEntity::getLastTimeSeen, timeQueryModel.startAt())
                .le(PeerRecordEntity::getLastTimeSeen, timeQueryModel.endAt());

        if (downloader != null && !downloader.isBlank()) {
            queryBanned.eq(HistoryEntity::getDownloader, downloader);
            queryConnected.eq(PeerRecordEntity::getDownloader, downloader);
        }

        List<String> ips = new ArrayList<>();
        if (bannedOnly) {
            historyService.list(queryBanned).stream().map(historyEntity -> historyEntity.getIp().getHostAddress()).distinct().forEach(ips::add);
        } else {
            peerRecordService.list(queryConnected).stream().map(peerRecordEntity -> peerRecordEntity.getAddress().getHostAddress()).distinct().forEach(ips::add);
        }

        try (ExecutorService service = Executors.newWorkStealingPool()) {
            for (String ip : ips) {
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
}
