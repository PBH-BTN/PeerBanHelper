package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.database.table.HistoryEntity;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.metric.HitRateMetricRecorder;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import io.javalin.http.Context;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.*;
import java.util.function.Function;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@IgnoreScan
public class PBHMetricsController extends AbstractFeatureModule {
    @Autowired
    @Qualifier("persistMetrics")
    private BasicMetrics metrics;
    @Autowired
    private HistoryDao historyDao;
    @Autowired
    private JavalinWebContainer webContainer;

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public void onEnable() {
        webContainer.javalin()
                .get("/api/statistic/counter", this::handleBasicCounter, Role.USER_READ)
                .get("/api/statistic/rules", this::handleRules, Role.USER_READ)
                .get("/api/statistic/analysis/field", this::handleHistoryNumberAccess, Role.USER_READ)
                .get("/api/statistic/analysis/date", this::handleHistoryDateAccess, Role.USER_READ);
    }

    private void handleHistoryDateAccess(Context ctx) throws Exception {
        String startAtArg = ctx.queryParam("startAt");
        String endAtArg = ctx.queryParam("endAt");
        String filter = Objects.requireNonNullElse(ctx.queryParam("filter"), "0.0");
        String type = ctx.queryParam("type");
        String field = ctx.queryParam("field");
        if (startAtArg == null) {
            throw new IllegalArgumentException("startAt cannot be null");
        }
        if (endAtArg == null) {
            throw new IllegalArgumentException("startAt cannot be null");
        }
        if (field == null) {
            throw new IllegalArgumentException("startAt cannot be null");
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
        long startAt = Long.parseLong(startAtArg);
        long endAt = Long.parseLong(endAtArg);
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
        List<HistoryDao.UniversalFieldNumResult> results = switch (type) {
            case "count" -> historyDao.countField(field, filter);
            case "sum" -> historyDao.sumField(field, filter);
            case null, default -> throw new IllegalArgumentException("type invalid");
        };
        ctx.json(new StdResp(true, null, results));
    }


    private void handleRules(Context ctx) {
        String locale = locale(ctx);
        Map<Rule, HitRateMetricRecorder> metric = new HashMap<>(getServer().getHitRateMetric().getHitRateMetric().asMap());
        Map<String, String> dict = new HashMap<>();
        List<RuleData> dat = metric.entrySet().stream()
                .map(obj -> {
                    TranslationComponent ruleType = new TranslationComponent(obj.getKey().getClass().getName());
                    if (obj.getKey().matcherName() != null) {
                        ruleType = obj.getKey().matcherName();
                    }
                    // 返回特定计算值作为字典键，这样不需要修改前端
                    dict.put(tl(locale, ruleType), tl(locale, ruleType));
                    return new RuleData(tl(locale, ruleType), obj.getValue().getHitCounter(), obj.getValue().getQueryCounter(), obj.getKey().metadata());
                })
                .sorted((o1, o2) -> Long.compare(o2.getHit(), o1.getHit()))
                .toList();
        Map<String, Object> resp = new HashMap<>();
        resp.put("dict", dict);
        resp.put("data", dat);
        ctx.json(new StdResp(true, null, resp));
    }

    private void handleBasicCounter(Context ctx) {
        Map<String, Object> map = new HashMap<>();
        map.put("checkCounter", metrics.getCheckCounter());
        map.put("peerBanCounter", metrics.getPeerBanCounter());
        map.put("peerUnbanCounter", metrics.getPeerUnbanCounter());
        map.put("banlistCounter", getServer().getBannedPeers().size());
        map.put("bannedIpCounter", getServer().getBannedPeers().keySet().stream().map(PeerAddress::getIp).distinct().count());
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

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    static class BanResponse {
        private String address;
        private BanMetadata banMetadata;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class RuleData {
        private String type;
        private long hit;
        private long query;
        private Map<String, Object> metadata;
    }

}
