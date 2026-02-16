package com.ghostchu.peerbanhelper.util.traversal.stun.tunnel;

public interface StunTcpTunnel extends AutoCloseable {
    void createMapping(int localPort);

    boolean isValid();

    long getLastSuccessHeartbeatAt();

    long getStartedAt();
}
