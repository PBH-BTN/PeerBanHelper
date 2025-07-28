package com.ghostchu.peerbanhelper.util.traversal.forwarder;

import com.ghostchu.peerbanhelper.util.SocketCopyWorker;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TCPForwarder {
    private final int proxyPort;
    private final String proxyHost;
    private final String remoteHost;
    private final int remotePort;

    private ServerSocket proxySocket;
    private volatile boolean running = false;
    private final ExecutorService netIOExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());

    public TCPForwarder(String proxyHost, int proxyPort, String remoteHost, int remotePort, String keepAliveHost, int keepAlivePort) {
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        log.info("TCPForwarder created: proxy {}:{}, remote {}:{}, keep-alive {}:{}", proxyHost, proxyPort, remoteHost, remotePort, keepAliveHost, keepAlivePort);
    }

    @SneakyThrows
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
                }
            } catch (IOException e) {
                if (running) {
                    log.error("Error accepting client connection", e);
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

            // 创建两个线程进行双向数据转发
            Socket finalRemoteSocket = remoteSocket;

            // 客户端到远程服务器的数据转发
            netIOExecutor.submit(()->new SocketCopyWorker(clientSocket, finalRemoteSocket).startSync());
            // 远程服务器到客户端的数据转发
           netIOExecutor.submit(()->new SocketCopyWorker(finalRemoteSocket, clientSocket).startSync());

        } catch (IOException e) {
            log.error("Error connecting to remote server: {}", e.getMessage());
            closeSocket(clientSocket);
            closeSocket(remoteSocket);
        }
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

    public void stop() {
        running = false;
        try {
            sched.shutdown();
            if (proxySocket != null) {
                proxySocket.close();
            }
            netIOExecutor.shutdown();
            try {
                if (!netIOExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    netIOExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                netIOExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        } catch (IOException e) {
            log.error("Error stopping TCP forwarder: {}", e.getMessage());
        }
    }
}