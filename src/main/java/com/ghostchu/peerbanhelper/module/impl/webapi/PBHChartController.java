package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.ghostchu.peerbanhelper.database.dao.impl.TrafficJournalDao;
import com.ghostchu.peerbanhelper.ipdb.IPDB;
import com.ghostchu.peerbanhelper.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.pbhplus.ActivationManager;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.WebUtil;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@Slf4j
@IgnoreScan
public class PBHChartController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private ActivationManager activationManager;
    @Autowired
    private PeerRecordDao peerRecordDao;
    @Autowired
    private HistoryDao historyDao;
    @Autowired
    private TrafficJournalDao trafficJournalDao;

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
        ;
    }

    private void handleTraffic(Context ctx) throws Exception {
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);

        String downloader = ctx.queryParam("downloader");
        if (downloader == null || downloader.isBlank()) {
            ctx.json(new StdResp(true, null, trafficJournalDao.getAllDownloadersOverallData(timeQueryModel.startAt(), timeQueryModel.endAt()).stream().peek(data -> fixTimezone(ctx, data)).toList()));
        } else {
            ctx.json(new StdResp(true, null, trafficJournalDao.getSpecificDownloaderOverallData(downloader, timeQueryModel.startAt(), timeQueryModel.endAt()).stream().peek(data -> fixTimezone(ctx, data)).toList()));
        }
    }

    private void handleTrafficClassic(Context ctx) throws Exception {
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        List<TrafficJournalDao.TrafficData> results = null;

        String downloader = ctx.queryParam("downloader");
        if (downloader == null || downloader.isBlank()) {
            results = trafficJournalDao.getAllDownloadersOverallData(timeQueryModel.startAt(), timeQueryModel.endAt()).stream().peek(data -> fixTimezone(ctx, data)).toList();
        } else {
            results = trafficJournalDao.getSpecificDownloaderOverallData(downloader, timeQueryModel.startAt(), timeQueryModel.endAt()).stream().peek(data -> fixTimezone(ctx, data)).toList();
        }

        List<TrafficJournalDao.TrafficData> records = new ArrayList<>();
        var it = results.iterator();
        // -----
        TrafficJournalDao.TrafficData base = null;
        while (it.hasNext()) {
            var target = it.next();

            if (base == null) {
                base = target;
                continue; // 跳过插入当天的总数据，直接计算增量
            }

            long uploadedOffset = target.getDataOverallUploaded() - base.getDataOverallUploaded();
            long downloadedOffset = target.getDataOverallDownloaded() - base.getDataOverallDownloaded();
            if (uploadedOffset < 0) uploadedOffset = 0;
            if (downloadedOffset < 0) downloadedOffset = 0;

            // 插入增量数据
            records.add(new TrafficJournalDao.TrafficData(target.getTimestamp(), uploadedOffset, downloadedOffset));
            base = target;
        }

//        if (records.size() > 1) {
//            // 多插的那个移除
//            records.removeFirst();
//        }
        ctx.json(new StdResp(true, null, records));
    }


    private void fixTimezone(Context ctx, TrafficJournalDao.TrafficData data) {
        Timestamp ts = data.getTimestamp();
        var epochSecond = ts.toLocalDateTime().atZone(timezone(ctx).toZoneId().getRules().getOffset(Instant.now()))
                .truncatedTo(ChronoUnit.DAYS).toEpochSecond();
        data.setTimestamp(new Timestamp(epochSecond * 1000));
    }

    private void handlePeerTrends(Context ctx) throws Exception {
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        Map<Long, AtomicInteger> connectedPeerTrends = new ConcurrentHashMap<>();
        Map<Long, AtomicInteger> bannedPeerTrends = new ConcurrentHashMap<>();
        try (var it = peerRecordDao.queryBuilder()
                .selectColumns("id", "lastTimeSeen")
                .where()
                .ge("lastTimeSeen", timeQueryModel.startAt())
                .and()
                .le("lastTimeSeen", timeQueryModel.endAt())
                .iterator()) {
            while (it.hasNext()) {
                var startOfDay = MiscUtil.getStartOfToday(it.next().getLastTimeSeen().getTime());
                connectedPeerTrends.computeIfAbsent(startOfDay, k -> new AtomicInteger()).addAndGet(1);
            }
        }
        try (var it = historyDao.queryBuilder()
                .selectColumns("id", "banAt")
                .where()
                .ge("banAt", timeQueryModel.startAt())
                .and()
                .le("banAt", timeQueryModel.endAt())
                .iterator()) {
            while (it.hasNext()) {
                var startOfDay = MiscUtil.getStartOfToday(it.next().getBanAt().getTime());
                bannedPeerTrends.computeIfAbsent(startOfDay, k -> new AtomicInteger()).addAndGet(1);
            }
        }
        ctx.json(new StdResp(true, null, Map.of(
                "connectedPeersTrend", connectedPeerTrends.entrySet().stream()
                        .map((e) -> new SimpleLongIntKV(e.getKey(), e.getValue().intValue()))
                        .sorted((o1, o2) -> Long.compare(o1.key(), o2.key))
                        .toList(),
                "bannedPeersTrend", bannedPeerTrends.entrySet().stream()
                        .map((e) -> new SimpleLongIntKV(e.getKey(), e.getValue().intValue()))
                        .sorted((o1, o2) -> Long.compare(o1.key(), o2.key))
                        .toList()
        )));
    }

    private void handleGeoIP(Context ctx) throws Exception {
        IPDB ipdb = getServer().getIpdb();
        if (ipdb == null) {
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.CHARTS_IPDB_NEED_INIT), null));
            return;
        }
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        var bannedOnly = Boolean.parseBoolean(ctx.queryParam("bannedOnly"));
        if (bannedOnly) {
            handleGeoIPBanned(ctx, timeQueryModel, ipdb);
        } else {
            handleGeoIPPeerRecord(ctx, timeQueryModel, ipdb);
        }
    }

    //@TODO: 底下写的一团糟，后面需要优化去重

    private void handleGeoIPBanned(Context ctx, WebUtil.TimeQueryModel timeQueryModel, IPDB ipdb) throws Exception {
        Map<String, AtomicInteger> ispCounter = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> cnProvinceCounter = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> cnCityCounter = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> countryOrRegionCounter = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> netTypeCounter = new ConcurrentHashMap<>();
        try (var it = historyDao.queryBuilder()
                .distinct()
                .selectColumns("id", "ip")
                .where()
                .ge("banAt", timeQueryModel.startAt())
                .and()
                .le("banAt", timeQueryModel.endAt())
                .iterator()) {
            try (ExecutorService service = Executors.newVirtualThreadPerTaskExecutor()) {
                while (it.hasNext()) {
                    var entity = it.next();
                    service.submit(() -> {
                        try {
                            IPGeoData ipGeoData = ipdb.query(InetAddress.getByName(entity.getIp()));
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
                            log.error("Unable to resolve the GeoIP data for ip {}", entity, e);
                        }
                    });
                }
            }
        }
        ctx.json(new StdResp(true, null, Map.of(
                "isp", ispCounter.entrySet().stream().map((e) -> new SimpleStringIntKV(e.getKey(), e.getValue().intValue()))
                        .sorted((o1, o2) -> Integer.compare(o2.value(), o1.value()))
                        .toList(),
                "province", cnProvinceCounter.entrySet().stream().map((e) -> new SimpleStringIntKV(e.getKey(), e.getValue().intValue()))
                        .sorted((o1, o2) -> Integer.compare(o2.value(), o1.value()))
                        .toList(),
                "region", countryOrRegionCounter.entrySet().stream().map((e) -> new SimpleStringIntKV(e.getKey(), e.getValue().intValue()))
                        .sorted((o1, o2) -> Integer.compare(o2.value(), o1.value()))
                        .toList(),
                "city", cnCityCounter.entrySet().stream().map((e) -> new SimpleStringIntKV(e.getKey(), e.getValue().intValue()))
                        .sorted((o1, o2) -> Integer.compare(o2.value(), o1.value()))
                        .toList()
        )));
    }

    private void handleGeoIPPeerRecord(Context ctx, WebUtil.TimeQueryModel timeQueryModel, IPDB ipdb) throws Exception {
        Map<String, AtomicInteger> ispCounter = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> cnProvinceCounter = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> cnCityCounter = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> countryOrRegionCounter = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> netTypeCounter = new ConcurrentHashMap<>();
        try (var it = peerRecordDao.queryBuilder()
                .distinct()
                .selectColumns("id", "address")
                .where()
                .ge("lastTimeSeen", timeQueryModel.startAt())
                .and()
                .le("lastTimeSeen", timeQueryModel.endAt())
                .iterator()) {
            try (ExecutorService service = Executors.newVirtualThreadPerTaskExecutor()) {
                while (it.hasNext()) {
                    var entity = it.next();
                    service.submit(() -> {
                        try {
                            IPGeoData ipGeoData = ipdb.query(InetAddress.getByName(entity.getAddress()));
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
                            log.error("Unable to resolve the GeoIP data for ip {}", entity, e);
                        }
                    });
                }
            }
        }
        ctx.json(new StdResp(true, null, Map.of(
                "isp", ispCounter.entrySet().stream().map((e) -> new SimpleStringIntKV(e.getKey(), e.getValue().intValue()))
                        .sorted((o1, o2) -> Integer.compare(o2.value(), o1.value()))
                        .toList(),
                "province", cnProvinceCounter.entrySet().stream().map((e) -> new SimpleStringIntKV(e.getKey(), e.getValue().intValue()))
                        .sorted((o1, o2) -> Integer.compare(o2.value(), o1.value()))
                        .toList(),
                "city", cnCityCounter.entrySet().stream().map((e) -> new SimpleStringIntKV(e.getKey(), e.getValue().intValue()))
                        .sorted((o1, o2) -> Integer.compare(o2.value(), o1.value()))
                        .toList(),
                "region", countryOrRegionCounter.entrySet().stream().map((e) -> new SimpleStringIntKV(e.getKey(), e.getValue().intValue()))
                        .sorted((o1, o2) -> Integer.compare(o2.value(), o1.value()))
                        .toList()
        )));
    }


    @Override
    public void onDisable() {

    }

    record SimpleStringIntKV(String key, int value) {

    }

    record SimpleLongIntKV(long key, int value) {

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
