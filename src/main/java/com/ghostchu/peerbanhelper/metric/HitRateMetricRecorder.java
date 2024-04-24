package com.ghostchu.peerbanhelper.metric;

import java.util.concurrent.atomic.AtomicLong;

public class HitRateMetricRecorder {
    private final AtomicLong query = new AtomicLong();
    private final AtomicLong hit = new AtomicLong();

    public void addQueryCounter() {
        query.incrementAndGet();
    }

    public void addHitCounter() {
        hit.incrementAndGet();
    }

    public long getQueryCounter() {
        return query.get();
    }

    public long getHitCounter() {
        return hit.get();
    }

    public void reset() {
        query.set(0);
        hit.set(0);
    }
}
