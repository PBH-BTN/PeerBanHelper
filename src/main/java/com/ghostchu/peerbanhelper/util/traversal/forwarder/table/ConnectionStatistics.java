package com.ghostchu.peerbanhelper.util.traversal.forwarder.table;

import java.util.concurrent.atomic.LongAdder;

public class ConnectionStatistics {
    private final LongAdder downloaded = new LongAdder();
    private final LongAdder uploaded = new LongAdder();

    public LongAdder getDownloaded() {
        return downloaded;
    }

    public LongAdder getUploaded() {
        return uploaded;
    }
}
