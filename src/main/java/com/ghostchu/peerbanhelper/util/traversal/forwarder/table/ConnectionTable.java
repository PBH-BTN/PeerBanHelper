package com.ghostchu.peerbanhelper.util.traversal.forwarder.table;

import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionTable extends ConcurrentHashMap<SocketAddress, SocketAddress> {
}
