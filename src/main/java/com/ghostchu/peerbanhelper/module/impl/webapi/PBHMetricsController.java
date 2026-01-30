package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.databasent.dto.UniversalFieldDateResult;
import com.ghostchu.peerbanhelper.databasent.dto.UniversalFieldNumResult;
import com.ghostchu.peerbanhelper.databasent.service.HistoryService;
import com.ghostchu.peerbanhelper.databasent.service.PeerConnectionMetricsService;
import com.ghostchu.peerbanhelper.databasent.service.TrackedSwarmService;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.SimpleOffsetDateTimeIntKVDTO;
import com.ghostchu.peerbanhelper.util.TimeUtil;
import com.ghostchu.peerbanhelper.util.WebUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@Slf4j
@Component
public final class PBHMetricsController extends AbstractFeatureModule {
    @Autowired
    @Qualifier("persistMetrics")
    private BasicMetrics metrics;
    @Autowired
    private HistoryService historyService;
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private DownloaderServer downloaderServer;
    @Autowired
    private TrackedSwarmService trackedSwarmDao;
    @Autowired
    private PeerConnectionMetricsService peerConnectionMetricDao;

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public void onEnable() {
        webContainer.javalin().unsafe.routes
                .get("/api/statistic/counter", this::handleBasicCounter, Role.USER_READ)
                .get("/api/statistic/analysis/field", this::handleHistoryNumberAccess, Role.USER_READ)
                .get("/api/statistic/analysis/banTrends", this::handleBanTrends, Role.USER_READ)
                .get("/api/statistic/analysis/date", this::handleHistoryDateAccess, Role.USER_READ);
    }

    private void handleBanTrends(Context ctx) {
        var downloader = ctx.queryParam("downloader");
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        Map<OffsetDateTime, AtomicInteger> bannedPeerTrends = new ConcurrentHashMap<>();
        var queryBanned = Wrappers.<HistoryEntity>lambdaQuery()
                .select(HistoryEntity::getId, HistoryEntity::getBanAt)
                .ge(HistoryEntity::getBanAt, timeQueryModel.startAt())
                .le(HistoryEntity::getBanAt, timeQueryModel.endAt());

        if (downloader != null && !downloader.isBlank()) {
            queryBanned.eq(HistoryEntity::getDownloader, downloader);
        }

        historyService.list(queryBanned).forEach(entity -> {
            var startOfDay = TimeUtil.getStartOfToday(entity.getBanAt());
            bannedPeerTrends.computeIfAbsent(startOfDay, k -> new AtomicInteger()).addAndGet(1);
        });

        ctx.json(new StdResp(true, null, bannedPeerTrends.entrySet().stream()
                .map((e) -> new SimpleOffsetDateTimeIntKVDTO(e.getKey(), e.getValue().intValue()))
                .sorted(Comparator.comparing(SimpleOffsetDateTimeIntKVDTO::key))
                .toList()
        ));
    }

    private void handleHistoryDateAccess(Context ctx) throws Exception {
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        String filter = Objects.requireNonNullElse(ctx.queryParam("filter"), "0.0");
        String type = ctx.queryParam("type");
        String field = ctx.queryParam("field");
        if (field == null) {
            throw new IllegalArgumentException("startAt cannot be null");
        }
        if (field.equalsIgnoreCase("banAt")) {
            if ("day".equals(type)) {
                // 劫持单独处理以加快首屏请求
                handlePeerBans(ctx);
                return;
            }
        }

        Function<Calendar, Calendar> trimmer = switch (type) {
            case "year" -> (time) -> {
                Calendar calendar = getZeroCalender();
                calendar.set(Calendar.YEAR, time.get(Calendar.YEAR));
                return calendar;
            };
            case "month" -> (time) -> {
                Calendar calendar = getZeroCalender();
                calendar.set(Calendar.YEAR, time.get(Calendar.YEAR));
                calendar.set(Calendar.MONTH, time.get(Calendar.MONTH));
                return calendar;
            };
            case "day" -> (time) -> {
                Calendar calendar = getZeroCalender();
                calendar.set(Calendar.YEAR, time.get(Calendar.YEAR));
                calendar.set(Calendar.MONTH, time.get(Calendar.MONTH));
                calendar.set(Calendar.DAY_OF_MONTH, time.get(Calendar.DAY_OF_MONTH));
                return calendar;
            };
            case "hour" -> (time) -> {
                Calendar calendar = getZeroCalender();
                calendar.set(Calendar.YEAR, time.get(Calendar.YEAR));
                calendar.set(Calendar.MONTH, time.get(Calendar.MONTH));
                calendar.set(Calendar.DAY_OF_MONTH, time.get(Calendar.DAY_OF_MONTH));
                calendar.set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY));
                return calendar;
            };
            case "minute" -> (time) -> {
                Calendar calendar = getZeroCalender();
                calendar.set(Calendar.YEAR, time.get(Calendar.YEAR));
                calendar.set(Calendar.MONTH, time.get(Calendar.MONTH));
                calendar.set(Calendar.DAY_OF_MONTH, time.get(Calendar.DAY_OF_MONTH));
                calendar.set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY));
                calendar.set(Calendar.MINUTE, time.get(Calendar.MINUTE));
                return calendar;
            };
            case "second" -> (time) -> {
                Calendar calendar = getZeroCalender();
                calendar.set(Calendar.YEAR, time.get(Calendar.YEAR));
                calendar.set(Calendar.MONTH, time.get(Calendar.MONTH));
                calendar.set(Calendar.DAY_OF_MONTH, time.get(Calendar.DAY_OF_MONTH));
                calendar.set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY));
                calendar.set(Calendar.MINUTE, time.get(Calendar.MINUTE));
                calendar.set(Calendar.SECOND, time.get(Calendar.SECOND));
                return calendar;
            };
            case null, default -> throw new IllegalArgumentException("Unexpected value: " + type);
        };

        Function<HistoryEntity, OffsetDateTime> timestampGetter = switch (field) {
            case "banAt" -> HistoryEntity::getBanAt;
            case "unbanAt" -> HistoryEntity::getUnbanAt;
            case null, default -> throw new IllegalArgumentException("Unexpected value: " + field);
        };

        double pctFilter = Double.parseDouble(filter);

        var query = Wrappers.<HistoryEntity>lambdaQuery()
                .select(HistoryEntity::getBanAt, HistoryEntity::getUnbanAt) // select both or optimize based on field
                .ge(HistoryEntity::getBanAt, timeQueryModel.startAt())
                .le(HistoryEntity::getBanAt, timeQueryModel.endAt());

        List<HistoryEntity> entities = historyService.list(query);
        Map<Long, AtomicInteger> map = new HashMap<>();
        for (HistoryEntity entity : entities) {
            OffsetDateTime time = timestampGetter.apply(entity);
            if (time == null) continue;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(time.toInstant().toEpochMilli());
            Calendar trimmed = trimmer.apply(cal);
            map.computeIfAbsent(trimmed.getTimeInMillis(), k -> new AtomicInteger()).incrementAndGet();
        }

        long total = map.values().stream().mapToLong(AtomicInteger::get).sum();
        List<UniversalFieldDateResult> results = new ArrayList<>();
        for (var entry : map.entrySet()) {
            double pct = (double) entry.getValue().get() / total;
            if (pct >= pctFilter) {
                results.add(new UniversalFieldDateResult(entry.getKey(), entry.getValue().get(), pct));
            }
        }
        ctx.json(new StdResp(true, null, results));
    }

    private Calendar getZeroCalender() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, 0);
        calendar.set(Calendar.MONTH, 0);
        calendar.set(Calendar.DAY_OF_MONTH, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private void handleHistoryNumberAccess(Context ctx) {
        // 过滤 X% 以下的数据
        String type = ctx.queryParam("type");
        String field = ctx.queryParam("field");
        double filter = Double.parseDouble(Objects.requireNonNullElse(ctx.queryParam("filter"), "0.0"));
        String downloader = ctx.queryParam("downloader");
        Integer substringLength = null;
        if ("peerId".equalsIgnoreCase(field) || "peer_id".equalsIgnoreCase(field)) {
            substringLength = 8;
        }
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        if (field == null) {
            throw new IllegalArgumentException("field cannot be null");
        }
        List<UniversalFieldNumResult> results = switch (type) {
            case "count" -> historyService.countField(field, filter, downloader, substringLength);
            case "sum" -> historyService.sumField(field, filter, downloader, substringLength);
            case null, default -> throw new IllegalArgumentException("type invalid");
        };
        ctx.json(new StdResp(true, null, results));
    }


    private void handlePeerBans(Context ctx) {
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        Map<Long, AtomicInteger> bannedPeerTrends = new ConcurrentHashMap<>();
        var query = Wrappers.<HistoryEntity>lambdaQuery()
                .select(HistoryEntity::getId, HistoryEntity::getBanAt)
                .ge(HistoryEntity::getBanAt, timeQueryModel.startAt())
                .le(HistoryEntity::getBanAt, timeQueryModel.endAt());

        historyService.list(query).forEach(entity -> {
            var startOfDay = TimeUtil.getStartOfToday(entity.getBanAt());
            bannedPeerTrends.computeIfAbsent(startOfDay.toInstant().toEpochMilli(), k -> new AtomicInteger()).addAndGet(1);
        });

        ctx.json(new StdResp(true, null, bannedPeerTrends.entrySet().stream().map((e) -> new UniversalFieldDateResult(e.getKey(), e.getValue().intValue(), 0)).toList()));
    }

    private void handleBasicCounter(Context ctx) {
        Map<String, Object> map = new HashMap<>();
        map.put("checkCounter", metrics.getCheckCounter());
        map.put("peerBanCounter", metrics.getPeerBanCounter());
        map.put("peerUnbanCounter", metrics.getPeerUnbanCounter());
        map.put("banlistCounter", downloaderServer.getBanList().size());
        // todo 这里需要改变
        map.put("bannedIpCounter", downloaderServer.getBanList().copyKeySet().stream().distinct().count());
        map.put("wastedTraffic", metrics.getWastedTraffic());
        //map.put("savedTraffic", metrics.getSavedTraffic());
        try {
            long trackedPeers = trackedSwarmDao.count();
            map.put("trackedSwarmCount", trackedPeers);
            if (trackedPeers > 0) {
                map.put("peersBlockRate", (double) metrics.getPeerBanCounter() / trackedPeers);
            } else {
                map.put("peersBlockRate", 0.0d);
            }
        } catch (Exception e) {
            map.put("peersBlockRate", 0.0d);
            log.error("Unable to query tracked swarm count", e);
        }
        var startWeekly = TimeUtil.getStartOfToday(System.currentTimeMillis() - 7 * 24 * 3600 * 1000);
        var endToday = TimeUtil.getStartOfToday(System.currentTimeMillis());
        map.put("weeklySessions", peerConnectionMetricDao.getGlobalTotalConnectionsCount(
                startWeekly,
                endToday));
        ctx.json(new StdResp(true, null, map));
    }

    @Override
    public void onDisable() {
        this.metrics = null;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Metrics";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-metrics";
    }
}
