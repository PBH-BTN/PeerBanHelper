package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.metric.HitRateMetric;
import com.ghostchu.peerbanhelper.metric.HitRateMetricRecorder;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.web.PBHAPI;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import fi.iki.elonen.NanoHTTPD;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

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
        // int pageIndex = Integer.parseInt(session.getParameters().getOrDefault("pageIndex", List.of("0")).get(0));
        // int pageSize = Integer.parseInt(session.getParameters().getOrDefault("pageSize", List.of("100")).get(0));
        List<RuleData> dat = metric.entrySet().stream()
                .map(obj -> new RuleData(obj.getKey().getClass().getSimpleName(), obj.getValue().getHitCounter(), obj.getValue().getQueryCounter(), obj.getKey().metadata()))
                .sorted((o1, o2) -> Long.compare(o2.getHit(), o1.getHit()))
                // .skip(Math.max((((long) pageIndex * pageSize) - 1), 0))
                // .limit(pageSize)
                .toList();
        ;
//        Map<String, Object> resp = new HashMap<>();
//        resp.put("size", metric.size());
////        resp.put("pageIndex", pageIndex);
////        resp.put("pageSize", pageSize);
//        resp.put("data", dat);
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.getGson().toJson(dat)));
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

    @Override
    public boolean needCheckHandshake() {
        return false;
    }

    @Override
    public @NotNull BanResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        return teapot();
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

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    static class BanResponse {
        private String address;
        private BanMetadata banMetadata;
    }
}
