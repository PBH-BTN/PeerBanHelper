package com.ghostchu.peerbanhelper.util.traversal.forwarder;

import com.ghostchu.peerbanhelper.util.traversal.forwarder.table.ConnectionStatistics;
import com.ghostchu.peerbanhelper.util.traversal.forwarder.table.ConnectionTable;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Map;

public interface Forwarder extends AutoCloseable {
    void start() throws IOException;

    long getEstablishedConnections();

    ConnectionTable getConnectionMap();

    Map<SocketAddress, ConnectionStatistics> getConnectionStats();
}
