package com.ghostchu.peerbanhelper.util.traversal.forwarder.table;

import com.ghostchu.peerbanhelper.util.ipdb.IPGeoData;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.LongAdder;

@Getter
public class ConnectionStatistics {
    private final LongAdder toUpstreamBytes = new LongAdder();
    private final LongAdder toDownstreamBytes = new LongAdder();
    private long establishedAt;
    private long lastActivityAt;
    @Setter
    private IPGeoData ipGeoData;

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
