package com.ghostchu.peerbanhelper.util.traversal.forwarder;

import com.ghostchu.peerbanhelper.ExternalSwitch;
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
    private final LongAdder connectionBlocked = new LongAdder();

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
                    connectionBlocked.increment();
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
        if (!(downstreamSocket.getRemoteSocketAddress() instanceof InetSocketAddress downstreamSocketAddress)) {
            closeSocket(downstreamSocket);
            return;
        }
        if (downstreamSocket.getRemoteSocketAddress() instanceof InetSocketAddress inetSocketAddress) {
            IPAddress downstreamIpAddress = IPAddressUtil.getIPAddress(inetSocketAddress.getAddress().getHostAddress());
            for (PeerAddress peerAddress : banListReference.keySet()) {
                if (peerAddress.getAddress().contains(downstreamIpAddress)) {
                    log.debug("Decline banned connection to connect from {}:{}", downstreamIpAddress, downstreamSocket.getPort());
                    try {
                        downstreamSocket.setOption(StandardSocketOptions.SO_LINGER, 1);
                        downstreamSocket.close();
                        connectionBlocked.increment();
                    } catch (IOException ignored) {
                    }
                    return;
                }
            }
        }
        Socket upstreamSocket = null;
        try {
            // 连接到远程服务器
            upstreamSocket = connectToUpstreamFriendly(downstreamSocketAddress);
            if (!(upstreamSocket.getRemoteSocketAddress() instanceof InetSocketAddress upstreamRemoteSocketAddress)) {
                closeSocket(upstreamSocket);
                return;
            }
            if (!(upstreamSocket.getLocalSocketAddress() instanceof InetSocketAddress upstreamLocalSocketAddress)) {
                closeSocket(upstreamSocket);
                return;
            }
            log.debug("Connected to upstream: {}, starting dual directory forward channel", new InetSocketAddress(upstremHost, upstreamPort));
            // 创建两个线程进行双向数据转发
            Socket finalUpstreamSocket = upstreamSocket;
            ConnectionStatistics statistics = new ConnectionStatistics();
            var toUpstreamBytes = statistics.getToUpstreamBytes();
            var toDownstreamBytes = statistics.getToDownstreamBytes();
            statistics.setEstablishedAt();
            // 客户端到远程服务器的数据转发
            netIOExecutor.submit(() -> new SocketCopyWorker(downstreamSocket, finalUpstreamSocket, (exception) -> onSocketClosed(downstreamSocketAddress, exception), toUpstreamBytes::add, statistics::setLastActivityAt).startSync());
            // 远程服务器到客户端的数据转发
            netIOExecutor.submit(() -> new SocketCopyWorker(finalUpstreamSocket, downstreamSocket, (exception) -> onSocketClosed(downstreamSocketAddress, exception), toDownstreamBytes::add, statistics::setLastActivityAt).startSync());
            connectionStats.put(downstreamSocketAddress, statistics);
            connectionMap.put(downstreamSocketAddress, upstreamLocalSocketAddress);
            socketAddressSocketMap.put(downstreamSocketAddress, downstreamSocket);
            socketAddressSocketMap.put(upstreamLocalSocketAddress, upstreamSocket);
        } catch (IOException e) {
            log.error("Error connecting to upstream server: {}", e.getMessage());
            closeSocket(downstreamSocket);
            closeSocket(upstreamSocket);
            connectionFailed.increment();
        }
    }

//    private Socket connectToUpstreamDefault() throws IOException {
//        Socket upstreamSocket = new Socket();
//        var upstreamAddress = new InetSocketAddress(upstremHost, upstreamPort);
//        upstreamSocket.connect(upstreamAddress, 5000);
//        return upstreamSocket;
//    }
// 友好绑定，优先使用本地原始 IP（第一个 byte 替换为 127），和原始端口；若失败退回本地原始 IP 和随机端口；再失败回退系统默认行为
    private Socket connectToUpstreamFriendly(InetSocketAddress downstreamSocket) throws IOException {
        Socket upstreamSocket = new Socket();
        var incomingAddress = downstreamSocket.getAddress();
        var upstreamAddress = new InetSocketAddress(upstremHost, upstreamPort);
        InetAddress outgoingAddress;
        if (upstreamAddress.getAddress().isLoopbackAddress() && downstreamSocket.getAddress() instanceof Inet4Address && ExternalSwitch.parseBoolean("pbh.TCPForwarder.useFriendlyAddressForLoopback", true)) {
            byte[] bytes = new byte[4];
            bytes[0] = 127;
            bytes[1] = incomingAddress.getAddress()[1];
            bytes[2] = incomingAddress.getAddress()[2];
            bytes[3] = incomingAddress.getAddress()[3];
            outgoingAddress = InetAddress.getByAddress(bytes);
            try {
                upstreamSocket.bind(new InetSocketAddress(outgoingAddress, downstreamSocket.getPort()));
            } catch (IOException ioe) {
                try {
                    upstreamSocket.bind(new InetSocketAddress(outgoingAddress, 0));
                } catch (IOException ignored) {
                } // okay default to random port and default ip...
            }
        }
        upstreamSocket.connect(upstreamAddress, 5000);
        return upstreamSocket;
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
    public long getConnectionBlocked() {
        return connectionBlocked.sum();
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