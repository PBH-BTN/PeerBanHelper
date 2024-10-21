package com.ghostchu.peerbanhelper.metric;

import com.ghostchu.peerbanhelper.util.rule.AbstractMatcher.MatcherInfo;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Getter;
import lombok.SneakyThrows;

import java.util.concurrent.TimeUnit;

@Getter
public class HitRateMetric {
    private final Cache<MatcherInfo, HitRateMetricRecorder> hitRateMetric =
            CacheBuilder
                    .newBuilder()
                    .expireAfterAccess(3, TimeUnit.DAYS)
                    .softValues()
                    .weakKeys()
                    .build();

    @SneakyThrows
    public void addQuery(MatcherInfo matcherinfo) {
        HitRateMetricRecorder recorder = hitRateMetric.get(matcherinfo, HitRateMetricRecorder::new);
        recorder.addQueryCounter();
    }

    @SneakyThrows
    public void addHit(MatcherInfo matcherinfo) {
        HitRateMetricRecorder recorder = hitRateMetric.get(matcherinfo, HitRateMetricRecorder::new);
        recorder.addHitCounter();
    }

}
