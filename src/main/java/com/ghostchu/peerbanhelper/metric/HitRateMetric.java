package com.ghostchu.peerbanhelper.metric;

import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Getter;
import lombok.SneakyThrows;

import java.util.concurrent.TimeUnit;

@Getter
public class HitRateMetric {
    private final Cache<Rule, HitRateMetricRecorder> hitRateMetric =
            CacheBuilder
                    .newBuilder()
                    .expireAfterAccess(3, TimeUnit.DAYS)
                    .build();

    @SneakyThrows
    public void addQuery(Rule object) {
        HitRateMetricRecorder recorder = hitRateMetric.get(object, HitRateMetricRecorder::new);
        recorder.addQueryCounter();
    }

    @SneakyThrows
    public void addHit(Rule object) {
        HitRateMetricRecorder recorder = hitRateMetric.get(object, HitRateMetricRecorder::new);
        recorder.addHitCounter();
    }

}
