package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.ghostchu.peerbanhelper.database.dao.impl.TrafficJournalDao;
import com.ghostchu.peerbanhelper.database.table.TrafficJournalEntity;
import com.ghostchu.peerbanhelper.ipdb.IPDB;
import com.ghostchu.peerbanhelper.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.pbhplus.ActivationManager;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.WebUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.exception.RequirePBHPlusLicenseException;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
                .get("/api/chart/geoip", this::handleGeoIP, Role.USER_READ)
                .get("/api/chart/trend", this::handlePeerTrends, Role.USER_READ)
                .get("/api/chart/traffic", this::handleTraffic, Role.USER_READ)
        ;
    }

    private void handleTraffic(Context ctx) throws Exception {
        if (!activationManager.isActivated()) {
            throw new RequirePBHPlusLicenseException(tl(locale(ctx), Lang.PBHPLUS_LICENSE_FAILED));
        }
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        String[] data = peerRecordDao.queryBuilder()
                .selectRaw("SUM(uploaded) as total_uploaded", "SUM(downloaded) as total_downloaded")
                .queryRawFirst();
        long totalUploaded = Long.parseLong(data[0]);
        long totalDownloaded = Long.parseLong(data[1]);

        List<TrafficJournalRecord> records = new ArrayList<>();
        // -----
        try (var it = trafficJournalDao.queryBuilder()
                .orderBy("timestamp", true)
                .where()
                .ge("timestamp", MiscUtil.getStartOfToday(timeQueryModel.startAt().getTime()))
                .and()
                .le("timestamp", MiscUtil.getStartOfToday(timeQueryModel.endAt().getTime()))
                .iterator()) {
            TrafficJournalEntity base = null;
            while (it.hasNext()) {
                if (base == null) {
                    base = it.next();
                    continue;
                }
                var target = it.next();
                long uploadedOffset = target.getUploaded() - base.getUploaded();
                long downloadedOffset = target.getDownloaded() - base.getDownloaded();
                base = target;
                records.add(new TrafficJournalRecord(base.getTimestamp(), uploadedOffset, downloadedOffset));
            }
        }
        ctx.json(new StdResp(true, null, new TrafficChart(totalUploaded, totalDownloaded, records)));
    }


    private void handlePeerTrends(Context ctx) throws Exception {
        if (!activationManager.isActivated()) {
            throw new RequirePBHPlusLicenseException(tl(locale(ctx), Lang.PBHPLUS_LICENSE_FAILED));
        }
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
        if (!activationManager.isActivated()) {
            throw new RequirePBHPlusLicenseException(tl(locale(ctx), Lang.PBHPLUS_LICENSE_FAILED));
        }
        IPDB ipdb = getServer().getIpdb();
        if (ipdb == null) {
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.CHARTS_IPDB_NEED_INIT), null));
            return;
        }
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);

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
                    var ip = it.next();
                    service.submit(() -> {
                        try {
                            IPGeoData ipGeoData = ipdb.query(InetAddress.getByName(ip.getAddress()));
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
                "isp", ispCounter.entrySet().stream().map((e) -> new SimpleStringIntKV(e.getKey(), e.getValue().intValue()))
                        .sorted((o1, o2) -> Integer.compare(o2.value(), o1.value()))
                        .toList(),
                "province", cnProvinceCounter.entrySet().stream().map((e) -> new SimpleStringIntKV(e.getKey(), e.getValue().intValue()))
                        .sorted((o1, o2) -> Integer.compare(o2.value(), o1.value()))
                        .toList(),
                "city", countryOrRegionCounter.entrySet().stream().map((e) -> new SimpleStringIntKV(e.getKey(), e.getValue().intValue()))
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

    record TrafficChart(
            long allTimeUploaded,
            long allTimeDownloaded,
            List<TrafficJournalRecord> journal
    ) {
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
