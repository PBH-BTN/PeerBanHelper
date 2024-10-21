package com.ghostchu.peerbanhelper.metric;

import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Getter;
import lombok.SneakyThrows;

import java.util.concurrent.TimeUnit;

@Getter
public class HitRateMetric {
    private final Cache<Rule.RuleInfo, HitRateMetricRecorder> hitRateMetric =
            CacheBuilder
                    .newBuilder()
                    .expireAfterAccess(3, TimeUnit.DAYS)
                    .build();

    @SneakyThrows
    public void addQuery(Rule rule) {
        HitRateMetricRecorder recorder = hitRateMetric.get(rule.toRuleInfo(), HitRateMetricRecorder::new);
        recorder.addQueryCounter();
    }

    @SneakyThrows
    public void addHit(Rule rule) {
        HitRateMetricRecorder recorder = hitRateMetric.get(rule.toRuleInfo(), HitRateMetricRecorder::new);
        recorder.addHitCounter();
    }

}
