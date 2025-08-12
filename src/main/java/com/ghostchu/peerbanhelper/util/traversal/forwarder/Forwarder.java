package com.ghostchu.peerbanhelper.util.traversal.forwarder;

import com.ghostchu.peerbanhelper.util.traversal.NatAddressProvider;
import com.ghostchu.peerbanhelper.util.traversal.forwarder.table.ConnectionStatistics;
import com.google.common.collect.BiMap;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

public interface Forwarder extends AutoCloseable, NatAddressProvider {
    void start() throws IOException;

    long getEstablishedConnections();

    BiMap<InetSocketAddress, InetSocketAddress> getDownstreamAddressAsKeyConnectionMap();

    BiMap<InetSocketAddress, InetSocketAddress> getProxyLAddressAsKeyConnectionMap();

    Map<InetSocketAddress, ConnectionStatistics> getDownstreamAddressAsKeyConnectionStats();

    long getTotalToUpstream();

    long getTotalToDownstream();

    long getConnectionFailed();

    long getConnectionHandled();

    long getConnectionBlocked();

    long getConnectionRejected();

    int getProxyPort();

    int getUpstreamPort();

    String getProxyHost();

    String getUpstremHost();

    ForwarderIOHandlerType getForwarderIOHandlerType();
}
