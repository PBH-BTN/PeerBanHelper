package com.ghostchu.peerbanhelper.util.traversal.forwarder;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.PeerBanEvent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.SocketCopyWorker;
import com.ghostchu.peerbanhelper.util.traversal.NatAddressProvider;
import com.ghostchu.peerbanhelper.util.traversal.forwarder.table.ConnectionStatistics;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.common.eventbus.Subscribe;
import inet.ipaddr.IPAddress;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/*
    @TODO: 需要重构：换 NIO 优化性能，但是好不容跑起来就先这样了
 */
@Slf4j
public class TCPForwarderImpl implements AutoCloseable, Forwarder, NatAddressProvider {
    private final int proxyPort;
    private final String proxyHost;
    private final String upstremHost;
    private final int upstreamPort;
    private final Map<PeerAddress, ?> banListReference;
    private ServerSocket proxySocket;
    private volatile boolean running = false;
    private final ExecutorService netIOExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
    //                  ClientAddress, ProxyLAddress
    private final BiMap<InetSocketAddress, InetSocketAddress> connectionMap = Maps.synchronizedBiMap(HashBiMap.create());
    private final Map<InetSocketAddress, ConnectionStatistics> connectionStats = Collections.synchronizedMap(new HashMap<>());
    private final Map<InetSocketAddress, Socket> socketAddressSocketMap = Collections.synchronizedMap(new HashMap<>());
    private final LongAdder connectionHandled = new LongAdder();
    private final LongAdder connectionFailed = new LongAdder();

    public TCPForwarderImpl(Map<PeerAddress, ?> banListReference, String proxyHost, int proxyPort, String upstreamHost, int upstreamPort) {
        this.banListReference = banListReference;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.upstremHost = upstreamHost;
        this.upstreamPort = upstreamPort;
        log.info("TCPForwarder created: proxy {}:{}, upstream {}:{}", proxyHost, proxyPort, upstreamHost, upstreamPort);
        sched.scheduleAtFixedRate(this::cleanupBannedConnections, 0, 15, TimeUnit.SECONDS);
        Main.getEventBus().register(this);
    }

    @Subscribe
    public void onPeerBanned(PeerBanEvent peerBanEvent) {
        var bannedPeerAddr = peerBanEvent.getPeer().getAddress();
        log.debug("Received PeerBanEvent for {}", bannedPeerAddr);
        for (Map.Entry<InetSocketAddress, InetSocketAddress> connectionPair : connectionMap.entrySet()) {
            if (connectionPair.getKey() instanceof InetSocketAddress inetSocketAddress) {
                var inetAddress = inetSocketAddress.getAddress();
                IPAddress downstreamAddress = IPAddressUtil.getIPAddress(inetAddress.getHostAddress());
                if (bannedPeerAddr.contains(downstreamAddress)) {
                    var downstreamSocket = socketAddressSocketMap.get(inetSocketAddress);
                    var upstreamSocket = socketAddressSocketMap.get(connectionPair.getValue());
                    log.debug("Closing connection from banned address from banEvent: {}", inetSocketAddress);
                    closeSocket(downstreamSocket);
                    closeSocket(upstreamSocket);
                }
            }
        }
    }


    @SneakyThrows
    @Override
    public void start() throws IOException {
        System.setProperty("sun.net.useExclusiveBind", "false");
        this.proxySocket = new ServerSocket();
        if (proxySocket.supportedOptions().contains(StandardSocketOptions.SO_REUSEPORT)) {
            proxySocket.setOption(StandardSocketOptions.SO_REUSEPORT, true);
        }
        if (proxySocket.supportedOptions().contains(StandardSocketOptions.SO_REUSEADDR)) {
            proxySocket.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        }
        log.debug("TCPForwarder starting at {}:{}", proxyHost, proxyPort);
        proxySocket.bind(new InetSocketAddress(proxyHost, proxyPort));
        running = true;
        netIOExecutor.submit(this::acceptConnections);
    }

    private void acceptConnections() {
        while (running) {
            try {
                Socket downstreamSocket = proxySocket.accept();
                if (downstreamSocket != null) {
                    // 为每个客户端连接创建一个处理线程
                    netIOExecutor.submit(() -> handleClient(downstreamSocket));
                    connectionHandled.increment();
                    log.debug("Accepted new downstream connection: {}", downstreamSocket.getRemoteSocketAddress());
                }
            } catch (IOException e) {
                if (running) {
                    log.error("Error accepting downstream connection", e);
                    connectionFailed.increment();
                }
            }
        }
    }

    private void handleClient(Socket downstreamSocket) {
        if (!(downstreamSocket.getRemoteSocketAddress() instanceof InetSocketAddress)) {
            closeSocket(downstreamSocket);
        }
        if (downstreamSocket.getRemoteSocketAddress() instanceof InetSocketAddress inetSocketAddress) {
            IPAddress downstreamIpAddress = IPAddressUtil.getIPAddress(inetSocketAddress.getAddress().getHostAddress());
            for (PeerAddress peerAddress : banListReference.keySet()) {
                if (peerAddress.getAddress().contains(downstreamIpAddress)) {
                    log.debug("Decline banned connection to connect from {}:{}", downstreamIpAddress, downstreamSocket.getPort());
                    try {
                        downstreamSocket.setOption(StandardSocketOptions.SO_LINGER, 1);
                        downstreamSocket.close();
                    } catch (IOException ignored) {
                    }
                    return;
                }
            }
        }
        Socket upstreamSocket = null;
        try {
            // 连接到远程服务器
            upstreamSocket = new Socket();
            upstreamSocket.connect(new InetSocketAddress(upstremHost, upstreamPort), 5000);
            if (!(upstreamSocket.getRemoteSocketAddress() instanceof InetSocketAddress)) {
                closeSocket(upstreamSocket);
            }
            if (!(upstreamSocket.getLocalSocketAddress() instanceof InetSocketAddress)) {
                closeSocket(upstreamSocket);
            }
            log.debug("Connected to upstream: {}, starting dual directory forward channel", new InetSocketAddress(upstremHost, upstreamPort));
            // 创建两个线程进行双向数据转发
            Socket finalUpstreamSocket = upstreamSocket;
            ConnectionStatistics statistics = new ConnectionStatistics();
            var toUpstreamBytes = statistics.getToUpstreamBytes();
            var toDownstreamBytes = statistics.getToDownstreamBytes();
            statistics.setEstablishedAt();
            // 客户端到远程服务器的数据转发
            netIOExecutor.submit(() -> new SocketCopyWorker(downstreamSocket, finalUpstreamSocket, (exception) -> onSocketClosed(downstreamSocket.getRemoteSocketAddress(), exception), toUpstreamBytes::add, statistics::setLastActivityAt).startSync());
            // 远程服务器到客户端的数据转发
            netIOExecutor.submit(() -> new SocketCopyWorker(finalUpstreamSocket, downstreamSocket, (exception) -> onSocketClosed(downstreamSocket.getRemoteSocketAddress(), exception), toDownstreamBytes::add, statistics::setLastActivityAt).startSync());
            connectionStats.put((InetSocketAddress) downstreamSocket.getRemoteSocketAddress(), statistics);
            connectionMap.put((InetSocketAddress) downstreamSocket.getRemoteSocketAddress(), (InetSocketAddress) upstreamSocket.getLocalSocketAddress());
            socketAddressSocketMap.put((InetSocketAddress) downstreamSocket.getRemoteSocketAddress(), downstreamSocket);
            socketAddressSocketMap.put((InetSocketAddress) upstreamSocket.getLocalSocketAddress(), upstreamSocket);
        } catch (IOException e) {
            log.error("Error connecting to upstream server: {}", e.getMessage());
            closeSocket(downstreamSocket);
            closeSocket(upstreamSocket);
            connectionFailed.increment();
        }
    }

    private void cleanupBannedConnections() {
        for (PeerAddress peerAddress : banListReference.keySet()) {
            IPAddress bannedAddress = peerAddress.getAddress();
            for (Map.Entry<InetSocketAddress, InetSocketAddress> connectionPair : connectionMap.entrySet()) {
                IPAddress clientAddress = IPAddressUtil.getIPAddress(connectionPair.getKey().getAddress().getHostAddress());
                if (bannedAddress.contains(clientAddress)) {
                    log.debug("Closing connection from banned address: {}", connectionPair.getKey());
                    Socket downstreamSocket = socketAddressSocketMap.get(connectionPair.getKey());
                    Socket upstreamSocket = socketAddressSocketMap.get(connectionPair.getValue());
                    closeSocket(downstreamSocket);
                    closeSocket(upstreamSocket);
                }
            }
        }
    }

    private void onSocketClosed(SocketAddress socketAddress, Exception e) {
        connectionMap.remove(socketAddress);
        connectionStats.remove(socketAddress);
    }

    @Override
    public long getEstablishedConnections() {
        return connectionMap.size();
    }

    @Override
    public BiMap<InetSocketAddress, InetSocketAddress> getDownstreamAddressAsKeyConnectionMap() {
        return connectionMap;
    }

    @Override
    public BiMap<InetSocketAddress, InetSocketAddress> getProxyLAddressAsKeyConnectionMap() {
        return connectionMap.inverse();
    }

    @Override
    public Map<InetSocketAddress, ConnectionStatistics> getDownstreamAddressAsKeyConnectionStats() {
        return connectionStats;
    }

    @Override
    public long getTotalDownloaded() {
        return connectionStats.values().stream().mapToLong(stats -> stats.getToUpstreamBytes().sum()).sum();
    }

    @Override
    public long getTotalUploaded() {
        return connectionStats.values().stream().mapToLong(stats -> stats.getToDownstreamBytes().sum()).sum();
    }

    @Override
    public long getConnectionFailed() {
        return connectionFailed.sum();
    }

    @Override
    public long getConnectionHandled() {
        return connectionHandled.sum();
    }

    @Override
    public int getProxyPort() {
        return proxyPort;
    }

    @Override
    public int getUpstreamPort() {
        return upstreamPort;
    }

    @Override
    public String getProxyHost() {
        return proxyHost;
    }

    @Override
    public String getUpstremHost() {
        return upstremHost;
    }

    private void closeSocket(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                log.error("Error closing socket: {}", e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        running = false;
        sched.shutdown();
        netIOExecutor.shutdown();
        try {
            if (proxySocket != null) {
                proxySocket.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (!netIOExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                netIOExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            netIOExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public @Nullable InetSocketAddress translate(@Nullable InetSocketAddress nattedAddress) {
        return connectionMap.inverse().get(nattedAddress);
    }
}