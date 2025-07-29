package com.ghostchu.peerbanhelper.util.traversal.forwarder;

import com.ghostchu.peerbanhelper.util.SocketCopyWorker;
import com.ghostchu.peerbanhelper.util.traversal.forwarder.table.ConnectionStatistics;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

/*
    @TODO: 需要重构：换 NIO 优化性能，但是好不容跑起来就先这样了
 */
@Slf4j
public class TCPForwarderImpl implements AutoCloseable, Forwarder {
    private final int proxyPort;
    private final String proxyHost;
    private final String remoteHost;
    private final int remotePort;
    private ServerSocket proxySocket;
    private volatile boolean running = false;
    private final ExecutorService netIOExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
    //                  ClientAddress, ProxyLAddress
    private final BiMap<SocketAddress, SocketAddress> connectionMap = HashBiMap.create();
    private final Map<SocketAddress, ConnectionStatistics> connectionStats = new ConcurrentHashMap<>();
    private final LongAdder connectionHandled = new LongAdder();
    private final LongAdder connectionFailed = new LongAdder();

    public TCPForwarderImpl(String proxyHost, int proxyPort, String remoteHost, int remotePort, String keepAliveHost, int keepAlivePort) {
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        log.info("TCPForwarder created: proxy {}:{}, remote {}:{}, keep-alive {}:{}", proxyHost, proxyPort, remoteHost, remotePort, keepAliveHost, keepAlivePort);
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
                Socket clientSocket = proxySocket.accept();
                if (clientSocket != null) {
                    // 为每个客户端连接创建一个处理线程
                    netIOExecutor.submit(() -> handleClient(clientSocket));
                    connectionHandled.increment();
                }
            } catch (IOException e) {
                if (running) {
                    log.error("Error accepting client connection", e);
                    connectionFailed.increment();
                }
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        Socket remoteSocket = null;
        try {
            // 连接到远程服务器
            remoteSocket = new Socket();
            remoteSocket.connect(new InetSocketAddress(remoteHost, remotePort), 5000);
            connectionMap.put(clientSocket.getRemoteSocketAddress(), remoteSocket.getLocalSocketAddress());
            // 创建两个线程进行双向数据转发
            Socket finalRemoteSocket = remoteSocket;
            ConnectionStatistics statistics = new ConnectionStatistics();
            var downloaded = statistics.getDownloaded();
            var uploaded = statistics.getUploaded();
            statistics.setEstablishedAt();
            // 客户端到远程服务器的数据转发
            netIOExecutor.submit(() -> new SocketCopyWorker(clientSocket, finalRemoteSocket, (exception) -> onSocketClosed(clientSocket.getRemoteSocketAddress(), exception), downloaded::add, statistics::setLastActivityAt).startSync());
            // 远程服务器到客户端的数据转发
            netIOExecutor.submit(() -> new SocketCopyWorker(finalRemoteSocket, clientSocket, (exception) -> onSocketClosed(clientSocket.getRemoteSocketAddress(), exception), uploaded::add, statistics::setLastActivityAt).startSync());
            connectionStats.put(clientSocket.getRemoteSocketAddress(), statistics);
        } catch (IOException e) {
            log.error("Error connecting to remote server: {}", e.getMessage());
            closeSocket(clientSocket);
            closeSocket(remoteSocket);
            connectionFailed.increment();
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
    public BiMap<SocketAddress, SocketAddress> getClientAddressAsKeyConnectionMap() {
        return connectionMap;
    }

    @Override
    public BiMap<SocketAddress, SocketAddress> getProxyLAddressAsKeyConnectionMap() {
        return connectionMap.inverse();
    }

    @Override
    public Map<SocketAddress, ConnectionStatistics> getClientAddressAsKeyConnectionStats() {
        return connectionStats;
    }

    @Override
    public long getTotalDownloaded() {
        return connectionStats.values().stream().mapToLong(stats -> stats.getDownloaded().sum()).sum();
    }

    @Override
    public long getTotalUploaded() {
        return connectionStats.values().stream().mapToLong(stats -> stats.getUploaded().sum()).sum();
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
    public int getRemotePort() {
        return remotePort;
    }

    @Override
    public String getProxyHost() {
        return proxyHost;
    }

    @Override
    public String getRemoteHost() {
        return remoteHost;
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
}