package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.metric.HitRateMetricRecorder;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class BtnAbilitySubmitRulesHitRate implements BtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final String endpoint;
    private final long randomInitialDelay;

    public BtnAbilitySubmitRulesHitRate(BtnNetwork btnNetwork, JsonObject ability) {
        this.btnNetwork = btnNetwork;
        this.interval = ability.get("interval").getAsLong();
        this.endpoint = ability.get("endpoint").getAsString();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();
    }

    @Override
    public void load() {
        btnNetwork.getExecuteService().scheduleWithFixedDelay(this::submit, interval + new Random().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void unload() {

    }


    private void submit() {
        try {
            log.info(tlUI(Lang.BTN_SUBMITTING_HITRATE));
            Map<Rule, HitRateMetricRecorder> metric = new HashMap<>(btnNetwork.getServer().getHitRateMetric().getHitRateMetric().asMap());
            List<RuleData> dat = metric.entrySet().stream()
                    .map(obj -> new RuleData(obj.getKey().getClass().getSimpleName(), obj.getValue().getHitCounter(), obj.getValue().getQueryCounter(), obj.getKey().metadata()))
                    .sorted((o1, o2) -> Long.compare(o2.getHit(), o1.getHit()))
                    .toList();
            Request request = new Request.Builder()
                    .url(endpoint)
                    .post(HTTPUtil.gzipBody(JsonUtil.getGson().toJson(dat).getBytes(StandardCharsets.UTF_8)))
                    .header("Content-Encoding", "gzip")
                    .build();
            HTTPUtil.nonRetryableSend(btnNetwork.getHttpClient(), request)
                    .thenAccept(r -> {
                        if (r.code() != 200) {
                            try {
                                log.error(tlUI(Lang.BTN_REQUEST_FAILS, r.code() + " - " + r.body().string()));
                            } catch (IOException ignored) {
                            }
                        } else {
                            log.info(tlUI(Lang.BTN_SUBMITTED_HITRATE, dat.size()));
                        }
                    })
                    .exceptionally(e -> {
                        log.error(tlUI(Lang.BTN_REQUEST_FAILS), e);
                        return null;
                    });
        } catch (Throwable throwable) {
            log.error("Unable to submit rules hit rate", throwable);
        }
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
