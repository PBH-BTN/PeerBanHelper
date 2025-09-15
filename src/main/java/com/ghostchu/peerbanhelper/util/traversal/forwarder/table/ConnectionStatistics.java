package com.ghostchu.peerbanhelper.util.traversal.forwarder.table;

import com.ghostchu.peerbanhelper.util.ipdb.IPGeoData;

import java.util.concurrent.atomic.LongAdder;

public class ConnectionStatistics {
    private final LongAdder toUpstreamBytes = new LongAdder();
    private final LongAdder toDownstreamBytes = new LongAdder();
    private long establishedAt;
    private long lastActivityAt;
    private IPGeoData ipGeoData;

    public LongAdder getToUpstreamBytes() {
        return toUpstreamBytes;
    }

    public LongAdder getToDownstreamBytes() {
        return toDownstreamBytes;
    }

    public long getEstablishedAt() {
        return establishedAt;
    }

    public long getLastActivityAt() {
        return lastActivityAt;
    }

    public IPGeoData getIpGeoData() {
        return ipGeoData;
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

    public void setIpGeoData(IPGeoData ipGeoData) {
        this.ipGeoData = ipGeoData;
    }
}
