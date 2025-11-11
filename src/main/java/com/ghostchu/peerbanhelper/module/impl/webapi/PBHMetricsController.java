package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.ghostchu.peerbanhelper.database.dao.impl.tmp.TrackedSwarmDao;
import com.ghostchu.peerbanhelper.database.table.HistoryEntity;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.SimpleLongIntKVDTO;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.WebUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.j256.ormlite.stmt.SelectArg;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
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
    private HistoryDao historyDao;
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private DownloaderServer downloaderServer;
    @Autowired
    private TrackedSwarmDao trackedSwarmDao;
    @Autowired
    private PeerRecordDao peerRecordDao;

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public void onEnable() {
        webContainer.javalin()
                .get("/api/statistic/counter", this::handleBasicCounter, Role.USER_READ)
                .get("/api/statistic/analysis/field", this::handleHistoryNumberAccess, Role.USER_READ)
                .get("/api/statistic/analysis/banTrends", this::handleBanTrends, Role.USER_READ)
                .get("/api/statistic/analysis/date", this::handleHistoryDateAccess, Role.USER_READ);
    }

    private void handleBanTrends(Context ctx) throws Exception {
        var downloader = ctx.queryParam("downloader");
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        Map<Long, AtomicInteger> bannedPeerTrends = new ConcurrentHashMap<>();
        var queryBanned = historyDao.queryBuilder()
                .selectColumns("id", "banAt")
                .where()
                .ge("banAt", timeQueryModel.startAt())
                .and()
                .le("banAt", timeQueryModel.endAt());
        if (downloader != null && !downloader.isBlank()) {
            queryBanned.and().eq("downloader", new SelectArg(downloader));
        }
        try (var it = queryBanned.iterator()) {
            while (it.hasNext()) {
                var startOfDay = MiscUtil.getStartOfToday(it.next().getBanAt().getTime());
                bannedPeerTrends.computeIfAbsent(startOfDay, k -> new AtomicInteger()).addAndGet(1);
            }
        }
        ctx.json(new StdResp(true, null, bannedPeerTrends.entrySet().stream()
                .map((e) -> new SimpleLongIntKVDTO(e.getKey(), e.getValue().intValue()))
                .sorted(Comparator.comparingLong(SimpleLongIntKVDTO::key))
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

        Function<HistoryEntity, Timestamp> timestampGetter = switch (field) {
            case "banAt" -> HistoryEntity::getBanAt;
            case "unbanAt" -> HistoryEntity::getUnbanAt;
            case null, default -> throw new IllegalArgumentException("Unexpected value: " + field);
        };
        long startAt = timeQueryModel.startAt().getTime();
        long endAt = timeQueryModel.endAt().getTime();
        double pctFilter = Double.parseDouble(filter);

        var results = historyDao.countDateField(startAt, endAt, timestampGetter, trimmer, pctFilter);
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

    private void handleHistoryNumberAccess(Context ctx) throws Exception {
        // 过滤 X% 以下的数据
        String type = ctx.queryParam("type");
        String field = ctx.queryParam("field");
        double filter = Double.parseDouble(Objects.requireNonNullElse(ctx.queryParam("filter"), "0.0"));
        String downloader = ctx.queryParam("downloader");
        Integer substringLength = null;
        if ("peerId".equalsIgnoreCase(field)) {
            substringLength = 8;
        }
        List<HistoryDao.UniversalFieldNumResult> results = switch (type) {
            case "count" -> historyDao.countField(field, filter, downloader, substringLength);
            case "sum" -> historyDao.sumField(field, filter, downloader, substringLength);
            case null, default -> throw new IllegalArgumentException("type invalid");
        };
        ctx.json(new StdResp(true, null, results));
    }


    private void handlePeerBans(Context ctx) throws Exception {
        var timeQueryModel = WebUtil.parseTimeQueryModel(ctx);
        Map<Long, AtomicInteger> bannedPeerTrends = new ConcurrentHashMap<>();
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
        ctx.json(new StdResp(true, null, bannedPeerTrends.entrySet().stream().map((e) -> new HistoryDao.UniversalFieldDateResult(e.getKey(), e.getValue().intValue(), 0)).toList()));
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
            long trackedPeers = trackedSwarmDao.countOf();
            map.put("trackedSwarmCount", trackedPeers);
            if (trackedPeers > 0) {
                map.put("peersBlockRate", (double) metrics.getPeerBanCounter() / trackedPeers);
            } else {
                map.put("peersBlockRate", 0.0d);
            }
        } catch (SQLException e) {
            map.put("peersBlockRate", 0.0d);
            log.error("Unable to query tracked swarm count", e);
        }
        try {
            var queryBuilder = peerRecordDao.queryBuilder();
            var where = queryBuilder.selectColumns("address").distinct().where();
            Timestamp weekAgo = new Timestamp(System.currentTimeMillis() - 7L * 24 * 3600 * 1000);
            Timestamp now = new Timestamp(System.currentTimeMillis());
            where.or(where.between("firstTimeSeen", weekAgo, now),
                    where.between("lastTimeSeen", weekAgo, now));
            map.put("weeklySessions", queryBuilder.countOf());
        } catch (SQLException e) {
            map.put("weeklySessions", 0);
            log.error("Unable to query weekly sessions", e);
        }
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
