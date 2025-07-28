package com.ghostchu.peerbanhelper.util.traversal.forwarder;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j
public class TCPForwarder {
    private final int localPort;
    private final String remoteHost;
    private final int remotePort;
    private final String localHost;
    private ServerSocketChannel serverSocket;
    private Selector selector;
    private volatile boolean running = false;
    private ExecutorService executor;

    // 存储客户端和远程连接的映射关系
    private final ConcurrentHashMap<SocketChannel, SocketChannel> clientToRemoteMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SocketChannel, SocketChannel> remoteToClientMap = new ConcurrentHashMap<>();
    private Socket keepAliveSocket;
    private ScheduledExecutorService sched = Executors.newScheduledThreadPool(1);

    public TCPForwarder(String localHost, int localPort, String remoteHost, int remotePort) {
        this.localHost = localHost;
        this.localPort = localPort;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void start() throws IOException {
        System.setProperty("sun.net.useExclusiveBind", "false");
        selector = Selector.open();
        this.serverSocket = ServerSocketChannel.open();

        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);

        if (serverSocket.supportedOptions().contains(StandardSocketOptions.SO_REUSEPORT)) {
            serverSocket.setOption(StandardSocketOptions.SO_REUSEPORT, true);
        }
        if (serverSocket.supportedOptions().contains(StandardSocketOptions.SO_REUSEADDR)) {
            serverSocket.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        }
        System.out.println("Creating: " + localHost + ":" + localPort );
        serverSocket.bind(new InetSocketAddress(localHost, localPort));
        running = true;
        executor.submit(this::run);
        startNATHolder();
    }

    private void run() {
        ByteBuffer buffer = ByteBuffer.allocate(8192);

        while (running) {
            try {
                selector.select(1000); // 1秒超时
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isConnectable()) {
                        handleConnect(key);
                    } else if (key.isReadable()) {
                        handleRead(key, buffer);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error in TCP forwarder: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);

            // 连接到远程服务器
            SocketChannel remoteChannel = SocketChannel.open();
            remoteChannel.configureBlocking(false);
            boolean connected = remoteChannel.connect(new InetSocketAddress(remoteHost, remotePort));

            // 建立映射关系
            clientToRemoteMap.put(clientChannel, remoteChannel);
            remoteToClientMap.put(remoteChannel, clientChannel);

            if (connected) {
                remoteChannel.register(selector, SelectionKey.OP_READ);
            } else {
                remoteChannel.register(selector, SelectionKey.OP_CONNECT);
            }
        }
    }

    private void handleConnect(SelectionKey key) throws IOException {
        SocketChannel remoteChannel = (SocketChannel) key.channel();

        if (remoteChannel.finishConnect()) {
            // 连接成功，注册读事件
            key.interestOps(SelectionKey.OP_READ);
        } else {
            // 连接失败，清理资源
            SocketChannel clientChannel = remoteToClientMap.remove(remoteChannel);
            if (clientChannel != null) {
                clientToRemoteMap.remove(clientChannel);
                closeChannel(clientChannel);
            }
            closeChannel(remoteChannel);
        }
    }

    private void handleRead(SelectionKey key, ByteBuffer buffer) throws IOException {
        SocketChannel sourceChannel = (SocketChannel) key.channel();
        buffer.clear();

        int bytesRead = sourceChannel.read(buffer);

        if (bytesRead > 0) {
            buffer.flip();

            // 找到目标通道
            SocketChannel targetChannel = clientToRemoteMap.get(sourceChannel);
            if (targetChannel == null) {
                targetChannel = remoteToClientMap.get(sourceChannel);
            }

            if (targetChannel != null && targetChannel.isConnected()) {
                // 转发数据
                while (buffer.hasRemaining()) {
                    targetChannel.write(buffer);
                }
            }
        } else if (bytesRead == -1) {
            // 连接关闭
            closeConnections(sourceChannel);
        }
    }

    private void closeConnections(SocketChannel channel) {
        try {
            SocketChannel peer = clientToRemoteMap.remove(channel);
            if (peer == null) {
                peer = remoteToClientMap.remove(channel);
            }

            if (peer != null) {
                clientToRemoteMap.remove(peer);
                remoteToClientMap.remove(peer);
                closeChannel(peer);
            }

            closeChannel(channel);
        } catch (Exception e) {
            System.err.println("Error closing connections: " + e.getMessage());
        }
    }

    private void closeChannel(SocketChannel channel) {
        try {
            SelectionKey key = channel.keyFor(selector);
            if (key != null) {
                key.cancel();
            }
            channel.close();
        } catch (IOException e) {
            System.err.println("Error closing channel: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;

        try {
            // 关闭所有连接
            for (SocketChannel client : clientToRemoteMap.keySet()) {
                closeChannel(client);
            }
            for (SocketChannel remote : remoteToClientMap.keySet()) {
                closeChannel(remote);
            }

            clientToRemoteMap.clear();
            remoteToClientMap.clear();

            if (serverSocket != null) {
                serverSocket.close();
            }
            if (selector != null) {
                selector.close();
            }
            if (executor != null) {
                executor.shutdown();
            }
        } catch (IOException e) {
            System.err.println("Error stopping TCP forwarder: " + e.getMessage());
        }
    }






    private void startNATHolder() {
        sched.scheduleAtFixedRate(this::keepAliveNATTunnel, 1L, 5L, TimeUnit.SECONDS);
    }

    private void keepAliveNATTunnel() {
        // send HEAD request to qq.com to keep NAT alive via getKeepAliveSocket()'s socket and retrieve HTTP status line
        try {
            Socket socket = getKeepAliveSocket();
            socket.getOutputStream().write(("HEAD / HTTP/1.1\r\nHost: qq.com\r\nUser-Agent: PeerBanHelper-NAT-Keeper/1.0\r\nConnection: keep-alive\r\n\r\n").getBytes());
            socket.getOutputStream().flush();
            String statusLine = new String(socket.getInputStream().readNBytes(1024));
            System.out.println(statusLine);
        } catch (IOException e) {
            log.warn("NAT保持连接失败", e);
        }

    }

    private Socket getKeepAliveSocket() throws IOException {
        if (this.keepAliveSocket != null && this.keepAliveSocket.isConnected() && !this.keepAliveSocket.isClosed()) {
            return keepAliveSocket;
        }
        System.out.println("Creating new keep-alive socket");
        System.setProperty("sun.net.useExclusiveBind", "false");
        this.keepAliveSocket = new Socket();
        if (keepAliveSocket.supportedOptions().contains(StandardSocketOptions.SO_REUSEPORT)) {
            keepAliveSocket.setOption(StandardSocketOptions.SO_REUSEPORT, true); // Enable port reuse on, you, *nix
        }
        if (keepAliveSocket.supportedOptions().contains(StandardSocketOptions.SO_REUSEADDR)) {
            keepAliveSocket.setOption(StandardSocketOptions.SO_REUSEADDR, true); // Enable address reuse on supported platform
        }
        keepAliveSocket.bind(new InetSocketAddress(localHost, localPort));
        keepAliveSocket.connect(new InetSocketAddress("qq.com", 80), 1000);
        return keepAliveSocket;
    }
}
