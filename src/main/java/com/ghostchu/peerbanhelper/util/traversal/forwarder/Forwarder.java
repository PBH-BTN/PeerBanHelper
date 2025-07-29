package com.ghostchu.peerbanhelper.util.traversal.forwarder;

import com.ghostchu.peerbanhelper.util.traversal.forwarder.table.ConnectionStatistics;
import com.google.common.collect.BiMap;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Map;

public interface Forwarder extends AutoCloseable {
    void start() throws IOException;

    long getEstablishedConnections();

    BiMap<SocketAddress, SocketAddress> getClientAddressAsKeyConnectionMap();

    BiMap<SocketAddress, SocketAddress> getProxyLAddressAsKeyConnectionMap();

    Map<SocketAddress, ConnectionStatistics> getClientAddressAsKeyConnectionStats();

    long getTotalDownloaded();

    long getTotalUploaded();

    long getConnectionFailed();

    long getConnectionHandled();

    int getProxyPort();

    int getRemotePort();

    String getProxyHost();

    String getRemoteHost();
}
