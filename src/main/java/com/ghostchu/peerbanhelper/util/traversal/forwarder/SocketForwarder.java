package com.ghostchu.peerbanhelper.util.traversal.forwarder;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
public class SocketForwarder {
    private final int localPort;
    private final String remoteHost;
    private final int remotePort;
    
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private ScheduledExecutorService speedUpdateExecutor;
    
    // 存储活动连接
    private final ConcurrentMap<String, ConnectionInfo> activeConnections = new ConcurrentHashMap<>();

    public SocketForwarder(int localPort, String remoteHost, int remotePort) {
        this.localPort = localPort;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    public void start() throws IOException {
        try {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            speedUpdateExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = Thread.ofVirtual().name("SocketForwarder-SpeedCalculator").unstarted(r);
                t.setDaemon(true);
                return t;
            });
            
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            InetSocketAddress clientAddress = ch.remoteAddress();
                            InetSocketAddress localAddress = ch.localAddress();
                            InetSocketAddress remoteAddress = new InetSocketAddress(remoteHost, remotePort);
                            
                            ConnectionInfo connectionInfo = new ConnectionInfo(clientAddress, localAddress, remoteAddress);
                            
                            activeConnections.put(clientAddress.getHostString()+":"+clientAddress.getPort(), connectionInfo);
                            log.info("Connection created: {}", connectionInfo.getConnectionStatus());
                            
                            ch.pipeline().addLast(new ProxyHandler(remoteHost, remotePort, SocketForwarder.this, connectionInfo));
                        }
                    });
            
            ChannelFuture future = bootstrap.bind(localPort).sync();
            serverChannel = future.channel();
            
            // 启动速度更新定时任务
            speedUpdateExecutor.scheduleWithFixedDelay(this::updateAllConnectionSpeeds, 1, 1, TimeUnit.SECONDS);
            log.debug("TCP转发服务器启动成功，监听端口: {}, 转发到: {}:{}", localPort, remoteHost, remotePort);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted when starting TCPForwarder", e);
        } catch (Exception e) {
            throw new IOException("Unable to start TCPForwarder", e);
        }
    }
    
    /**
     * 停止转发服务器
     */
    public void stop() {
        if (speedUpdateExecutor != null && !speedUpdateExecutor.isShutdown()) {
            speedUpdateExecutor.shutdown();
        }
        
        if (serverChannel != null) {
            serverChannel.close();
        }
        
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        
        activeConnections.clear();
    }
    
    /**
     * 移除连接
     */
    public void removeConnection(String connectionPair) {
        ConnectionInfo removed = activeConnections.remove(connectionPair);
        if (removed != null) {
            log.debug("Connection disconnected: {}", removed.getConnectionStatus());
        }
    }
    
    /**
     * 更新所有连接的速度
     */
    private void updateAllConnectionSpeeds() {
        activeConnections.values().forEach(ConnectionInfo::updateSpeed);
    }
    
    /**
     * 获取活动连接信息
     * @return 活动连接列表，格式：1.2.3.4:5555 -> 127.0.0.1:2314 -> 5.6.7.8:8888   [UP:103KB, DOWN:512MB] (15KB/s， 222KB/s)
     */
    public List<String> getActiveConnections() {
        return activeConnections.values().stream()
                .map(ConnectionInfo::getConnectionStatus)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取活动连接数量
     */
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }
    
    /**
     * 获取总上传流量
     */
    public long getTotalUploadBytes() {
        return activeConnections.values().stream()
                .mapToLong(conn -> conn.getUploadBytes().get())
                .sum();
    }
    
    /**
     * 获取总下载流量
     */
    public long getTotalDownloadBytes() {
        return activeConnections.values().stream()
                .mapToLong(conn -> conn.getDownloadBytes().get())
                .sum();
    }
    
    /**
     * 获取总上传速度
     */
    public long getTotalUploadSpeed() {
        return activeConnections.values().stream()
                .mapToLong(ConnectionInfo::getUploadSpeed)
                .sum();
    }
    
    /**
     * 获取总下载速度
     */
    public long getTotalDownloadSpeed() {
        return activeConnections.values().stream()
                .mapToLong(ConnectionInfo::getDownloadSpeed)
                .sum();
    }
    
    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return serverChannel != null && serverChannel.isActive();
    }
}
