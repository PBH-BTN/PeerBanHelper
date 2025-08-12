package com.ghostchu.peerbanhelper.util.traversal.stun.tunnel;

import java.io.IOException;

public interface StunTcpTunnel extends AutoCloseable {
    void createMapping(int localPort) throws IOException;

    boolean isValid();

    long getLastSuccessHeartbeatAt();

    long getStartedAt();
}
