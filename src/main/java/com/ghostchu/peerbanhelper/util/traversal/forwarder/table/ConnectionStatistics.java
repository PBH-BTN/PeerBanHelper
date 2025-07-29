package com.ghostchu.peerbanhelper.util.traversal.forwarder.table;

import java.util.concurrent.atomic.LongAdder;

public class ConnectionStatistics {
    private final LongAdder downloaded = new LongAdder();
    private final LongAdder uploaded = new LongAdder();
    private long establishedAt;
    private long lastActivityAt;

    public LongAdder getDownloaded() {
        return downloaded;
    }

    public LongAdder getUploaded() {
        return uploaded;
    }

    public long getEstablishedAt() {
        return establishedAt;
    }

    public long getLastActivityAt() {
        return lastActivityAt;
    }

    public void setEstablishedAt() {
        this.establishedAt = System.currentTimeMillis();
    }

    public void setLastActivityAt() {
        this.lastActivityAt = System.currentTimeMillis();
    }

    public void setEstablishedAt(long establishedAt) {
        this.establishedAt = establishedAt;
    }

    public void setLastActivityAt(long lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }
}
