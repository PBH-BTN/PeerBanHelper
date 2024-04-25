package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.metric.HitRateMetric;
import com.ghostchu.peerbanhelper.metric.HitRateMetricRecorder;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.web.PBHAPI;
import fi.iki.elonen.NanoHTTPD;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PBHRuleMetrics extends AbstractFeatureModule implements PBHAPI {

    private HitRateMetric metrics;

    public PBHRuleMetrics(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public boolean shouldHandle(String uri) {
        return uri.equals("/api/ruleStatistic");
    }

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) {
        Map<Rule, HitRateMetricRecorder> metric = new HashMap<>(metrics.getHitRateMetric());
        Map<String, String> dict = new HashMap<>();
        List<RuleData> dat = metric.entrySet().stream()
                .map(obj -> {
                    String ruleType = obj.getKey().getClass().getName();
                    if (obj.getKey().matcherName() != null) {
                        ruleType = obj.getKey().matcherName();
                    }
                    dict.put(obj.getKey().matcherIdentifier(), ruleType);
                    return new RuleData(obj.getKey().matcherIdentifier(), obj.getValue().getHitCounter(), obj.getValue().getQueryCounter(), obj.getKey().metadata());
                })
                .sorted((o1, o2) -> Long.compare(o2.getHit(), o1.getHit()))
                .toList();


        Map<String, Object> resp = new HashMap<>();
        resp.put("dict", dict);
        resp.put("data", dat);

        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.getGson().toJson(resp)));
    }

    @Override
    public void onEnable() {
        this.metrics = getServer().getHitRateMetric();
        getServer().getWebManagerServer().register(this);
    }

    @Override
    public void onDisable() {
        this.metrics = null;
        getServer().getWebManagerServer().unregister(this);
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Rule Metrics";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-rule-metrics";
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
