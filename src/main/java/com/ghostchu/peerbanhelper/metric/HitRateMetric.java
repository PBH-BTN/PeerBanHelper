package com.ghostchu.peerbanhelper.metric;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class HitRateMetric {
    @Getter
    private final Map<Object, HitRateMetricRecorder> hitRateMetric = new HashMap<>();

    public void addQuery(Object object) {
        HitRateMetricRecorder recorder = hitRateMetric.get(object);
        if (recorder == null) {
            recorder = new HitRateMetricRecorder();
        }
        recorder.addQueryCounter();
        hitRateMetric.put(object, recorder);
    }

    public void addHit(Object object) {
        HitRateMetricRecorder recorder = hitRateMetric.get(object);
        if (recorder == null) {
            recorder = new HitRateMetricRecorder();
        }
        recorder.addHitCounter();
        hitRateMetric.put(object, recorder);
    }


}
