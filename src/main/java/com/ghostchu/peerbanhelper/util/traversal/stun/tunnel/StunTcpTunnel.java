package com.ghostchu.peerbanhelper.util.traversal.stun.tunnel;

import com.ghostchu.peerbanhelper.util.observable.ReportGenerator;

public interface StunTcpTunnel extends AutoCloseable, ReportGenerator {
    void createMapping(int localPort);

    boolean isValid();

    long getLastSuccessHeartbeatAt();

    long getStartedAt();
}
