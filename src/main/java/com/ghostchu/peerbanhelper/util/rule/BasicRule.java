package com.ghostchu.peerbanhelper.util.rule;

import java.util.concurrent.atomic.AtomicLong;

public abstract class BasicRule implements Rule {
    private final AtomicLong queryCounter = new AtomicLong();
    private final AtomicLong hitCounter = new AtomicLong();

    public void recordHit() {
        hitCounter.incrementAndGet();
    }

    public void recordQuery() {
        queryCounter.incrementAndGet();
    }

    public long getHitCounter() {
        return hitCounter.get();
    }

    public long getQueryCounter() {
        return hitCounter.get();
    }
}
