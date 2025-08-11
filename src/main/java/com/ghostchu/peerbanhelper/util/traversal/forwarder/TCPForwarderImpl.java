package com.ghostchu.peerbanhelper.util.traversal.forwarder;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.PeerBanEvent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.traversal.NatAddressProvider;
import com.ghostchu.peerbanhelper.util.traversal.forwarder.iohandler.*;
import com.ghostchu.peerbanhelper.util.traversal.forwarder.table.ConnectionStatistics;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Maps;
import com.google.common.eventbus.Subscribe;
import inet.ipaddr.IPAddress;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.uring.IoUring;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
public class TCPForwarderImpl implements AutoCloseable, Forwarder, NatAddressProvider {
    private final int proxyPort;
    private final String proxyHost;
    private final String upstreamHost;
    private final int upstreamPort;
    private final Map<PeerAddress, ?> banListReference;

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());

    private final BiMap<InetSocketAddress, InetSocketAddress> connectionMap = Maps.synchronizedBiMap(HashBiMap.create());
    private final Map<InetSocketAddress, ConnectionStatistics> connectionStats = new ConcurrentHashMap<>();
    private final Map<InetSocketAddress, Channel> downstreamChannelMap = new ConcurrentHashMap<>();

    private final LongAdder connectionHandled = new LongAdder();
    private final LongAdder connectionFailed = new LongAdder();
    private final LongAdder connectionBlocked = new LongAdder();

    private final LongAdder totalToUpstream = new LongAdder();
    private final LongAdder totalToDownstream = new LongAdder();

    private Channel serverChannel;
    private final ForwarderIOHandler ioHandler;

    public TCPForwarderImpl(Map<PeerAddress, ?> banListReference, String proxyHost, int proxyPort, String upstreamHost, int upstreamPort) {
        this.banListReference = banListReference;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.upstreamHost = upstreamHost;
        this.upstreamPort = upstreamPort;
        if (IoUring.isAvailable()) { // 性能最好
            ioHandler = new IOUringHandler();
        } else if (Epoll.isAvailable()) { // 性能很不错！
            ioHandler = new EpollHandler();
        } else if (KQueue.isAvailable()) { // FreeBSD/MacOS
            ioHandler = new KQueueHandler();
        } else { // oh shit
            ioHandler = new NioHandler();
        }
        log.debug("IOHandler selected: {}", ioHandler.getClass().getSimpleName());
        this.bossGroup = new MultiThreadIoEventLoopGroup(ioHandler.ioHandlerFactory());
        this.workerGroup = new MultiThreadIoEventLoopGroup(ioHandler.ioHandlerFactory());
        log.debug("Netty TCPForwarder created: proxy {}:{}, upstream {}:{}", proxyHost, proxyPort, upstreamHost, upstreamPort);
        sched.scheduleAtFixedRate(this::cleanupBannedConnections, 0, 30, TimeUnit.SECONDS);
        Main.getEventBus().register(this);
    }

    @Override
    public void start() {
        ServerBootstrap b = new ServerBootstrap();
        ioHandler.apply(b.group(bossGroup, workerGroup)
                        .channel(ioHandler.serverSocketChannelClass())
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(new ProxyFrontendHandler());
                            }
                        }))
                .option(ChannelOption.SO_BACKLOG, 128)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.AUTO_READ, false); // 在连接到上游之前，暂停读取

        try {
            serverChannel = b.bind(proxyHost, proxyPort).sync().channel();
            log.debug("Netty TCPForwarder started and listening on {}:{}", proxyHost, proxyPort);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Netty TCPForwarder failed to start", e);
            close();
        }
    }

    @Subscribe
    public void onPeerBanned(PeerBanEvent peerBanEvent) {
        var bannedPeerAddr = peerBanEvent.getPeer().getAddress();
        log.debug("Received PeerBanEvent for {}", bannedPeerAddr);
        downstreamChannelMap.forEach((address, channel) -> {
            var inetAddress = address.getAddress();
            IPAddress downstreamAddress = IPAddressUtil.getIPAddress(inetAddress.getHostAddress());
            if (bannedPeerAddr.contains(downstreamAddress)) {
                log.debug("Closing connection from banned address from banEvent: {}", address);
                // 找到对应的上游和下游 Channel 并关闭
                closeConnectionByDownstreamAddress(address);
                connectionBlocked.increment();
            }
        });
    }

    private void cleanupBannedConnections() {
        banListReference.keySet().forEach(peerAddress -> {
            IPAddress bannedAddress = peerAddress.getAddress();
            downstreamChannelMap.forEach((clientAddress, channel) -> {
                IPAddress clientIp = IPAddressUtil.getIPAddress(clientAddress.getAddress().getHostAddress());
                if (bannedAddress.contains(clientIp)) {
                    log.debug("Closing connection from banned address during cleanup: {}", clientAddress);
                    closeConnectionByDownstreamAddress(clientAddress);
                }
            });
        });
    }

    private void closeConnectionByDownstreamAddress(InetSocketAddress downstreamAddress) {
        Channel downstreamChannel = downstreamChannelMap.get(downstreamAddress);
        if (downstreamChannel != null && downstreamChannel.hasAttr(RelayHandler.RELAY_CHANNEL_KEY)) {
            Channel upstreamChannel = downstreamChannel.attr(RelayHandler.RELAY_CHANNEL_KEY).get();
            if (upstreamChannel != null) {
                upstreamChannel.close();
            }
        }
        if (downstreamChannel != null) {
            downstreamChannel.close();
        }
    }

    @ChannelHandler.Sharable
    public class ProxyFrontendHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            final Channel downstreamChannel = ctx.channel();
            InetSocketAddress downstreamSocketAddress = (InetSocketAddress) downstreamChannel.remoteAddress();
            // 检查连接表，检查 IP 地址是否已有另一个链接，不允许多重连接，会干扰 ProgressCheatBlocker
            if (isDuplicateConnection(downstreamSocketAddress)) {
                log.debug("Multiple connections from the same IP address detected: {}, disconnecting...", downstreamSocketAddress.getAddress().getHostAddress());
                downstreamChannel.close();
                return;
            }
            // --- Ban Check ---
            IPAddress downstreamIpAddress = IPAddressUtil.getIPAddress(downstreamSocketAddress.getAddress().getHostAddress());
            if (ifBannedAddress(downstreamIpAddress)) {
                log.debug("Decline banned connection from {}:{}", downstreamIpAddress, downstreamSocketAddress.getPort());
                downstreamChannel.close();
                connectionBlocked.increment();
                return;
            }

            connectionHandled.increment();
            log.debug("Accepted new downstream connection: {}", downstreamSocketAddress);

            // --- Connect to Upstream ---
            Bootstrap b = new Bootstrap();
            b.group(downstreamChannel.eventLoop()) // 使用同一个 EventLoop 避免线程切换
                    .channel(ioHandler.clientSocketChannelClass())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new RelayHandler(downstreamChannel));
                        }
                    })
                    .option(ChannelOption.SO_KEEPALIVE, true);

            Thread.ofVirtual().name("Connection establisher").start(() -> {
                // --- Friendly Address Binding Logic ---
                ChannelFuture connectFuture = connectToUpstreamFriendly(b, downstreamSocketAddress);
                connectFuture.addListener((ChannelFuture future) -> {
                    if (future.isSuccess()) {
                        final Channel upstreamChannel = future.channel();
                        log.debug("Connected to upstream: {}, starting dual directory forward channel", upstreamChannel.remoteAddress());

                        // 连接成功，将下游的处理器替换为中继处理器
                        ctx.pipeline().addLast(new RelayHandler(upstreamChannel));
                        // 绑定双向 Channel 引用
                        downstreamChannel.attr(RelayHandler.RELAY_CHANNEL_KEY).set(upstreamChannel);
                        upstreamChannel.attr(RelayHandler.RELAY_CHANNEL_KEY).set(downstreamChannel);

                        // 记录连接信息
                        ConnectionStatistics stats = new ConnectionStatistics();
                        stats.setEstablishedAt();

                        InetSocketAddress upstreamLocalSocketAddress = (InetSocketAddress) upstreamChannel.localAddress();
                        connectionMap.put(downstreamSocketAddress, upstreamLocalSocketAddress);
                        connectionStats.put(downstreamSocketAddress, stats);
                        downstreamChannelMap.put(downstreamSocketAddress, downstreamChannel);

                        // 添加统计处理器
                        downstreamChannel.pipeline().addFirst(new TrafficCounterHandler(stats, true, totalToUpstream, totalToDownstream));
                        upstreamChannel.pipeline().addFirst(new TrafficCounterHandler(stats, false, totalToUpstream, totalToDownstream));

                        // 移除当前处理器
                        ctx.pipeline().remove(this);
                        // 全部设置完毕，开始读取下游数据
                        downstreamChannel.config().setAutoRead(true);
                    } else {
                        log.error("Error connecting to upstream server", future.cause());
                        downstreamChannel.close();
                        connectionFailed.increment();
                    }
                });
            });
        }

        private boolean ifBannedAddress(IPAddress downstreamIpAddress) {
            for (PeerAddress peerAddress : banListReference.keySet()) {
                if (peerAddress.getAddress().contains(downstreamIpAddress)) {
                    return true;
                }
            }
            return false;
        }

        private ChannelFuture connectToUpstreamFriendly(Bootstrap b, InetSocketAddress downstreamSocket) {
            InetAddress incomingAddress = downstreamSocket.getAddress();
            InetSocketAddress upstreamAddress = new InetSocketAddress(upstreamHost, upstreamPort);

            if (upstreamAddress.getAddress().isLoopbackAddress() && incomingAddress instanceof Inet4Address && ExternalSwitch.parseBoolean("pbh.TCPForwarder.useFriendlyAddressForLoopback", true)) {
                try {
                    byte[] bytes = new byte[4];
                    bytes[0] = 127;
                    bytes[1] = incomingAddress.getAddress()[1];
                    bytes[2] = incomingAddress.getAddress()[2];
                    bytes[3] = incomingAddress.getAddress()[3];
                    InetAddress outgoingAddress = InetAddress.getByAddress(bytes);
                    try {
                        ChannelFuture firstAttempt = b.localAddress(outgoingAddress, downstreamSocket.getPort()).connect(upstreamAddress).syncUninterruptibly();
                        if (firstAttempt.isSuccess()) {
                            return firstAttempt;
                        } else {
                            throw new IllegalStateException("Failed to bind to friendly address " + outgoingAddress.getHostAddress() + ":" + downstreamSocket.getPort());
                        }
                    } catch (Exception e) {
                        // 尝试绑定到友好地址和原始端口
                        log.debug("Failed to bind to friendly address {}:{}, trying random port.", outgoingAddress.getHostAddress(), downstreamSocket.getPort());
                        // 失败则退回，绑定到友好地址和随机端口
                        try {
                            Bootstrap b2 = b.clone();
                            ChannelFuture secondAttempt = b2.localAddress(outgoingAddress, 0).connect(upstreamAddress).syncUninterruptibly();
                            if (secondAttempt.isSuccess()) {
                                return secondAttempt;
                            }
                        } catch (Exception e2) {
                            log.debug("Failed to bind to friendly address {} with random port as well.", outgoingAddress.getHostAddress());
                        }
                    }
                } catch (UnknownHostException e) {
                    // fall through to default
                }
            }
            Bootstrap b3 = b.clone();
            log.debug("Failed to bind to friendly address, falling back to default.");
            return b3.localAddress(null).connect(upstreamAddress);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (!(cause instanceof IOException)) {
                log.debug("Exception in ProxyFrontendHandler", cause);
            }
            ctx.close();
            connectionFailed.increment();
        }
    }

    private boolean isDuplicateConnection(InetSocketAddress downstreamSocketAddress) {
        for (InetSocketAddress inetSocketAddress : connectionMap.keySet()) {
            if (Arrays.equals(downstreamSocketAddress.getAddress().getAddress(), inetSocketAddress.getAddress().getAddress())) {
                return true;
            }
        }
        return false;
    }


    public class RelayHandler extends ChannelInboundHandlerAdapter {
        public static final AttributeKey<Channel> RELAY_CHANNEL_KEY = AttributeKey.valueOf("relayChannel");
        private final Channel relayChannel;

        public RelayHandler(Channel relayChannel) {
            this.relayChannel = relayChannel;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.read(); // 开始读取数据
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (relayChannel.isActive()) {
                relayChannel.writeAndFlush(msg).addListener((ChannelFuture future) -> {
                    if (future.isSuccess()) {
                        ctx.channel().read();
                    } else {
                        future.channel().close();
                    }
                });
            } else {
                io.netty.util.ReferenceCountUtil.release(msg);
                ctx.close();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (relayChannel.isActive()) {
                relayChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
            cleanupConnectionState(ctx.channel());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (!(cause instanceof java.io.IOException)) {
                log.debug("Exception in RelayHandler from {}", ctx.channel().remoteAddress(), cause);
            }
            ctx.close();
            if (relayChannel.isActive()) {
                relayChannel.close();
            }
        }

        private void cleanupConnectionState(Channel channel) {
            Channel downstreamChannel = channel.hasAttr(RELAY_CHANNEL_KEY) ? channel.attr(RELAY_CHANNEL_KEY).get().attr(RELAY_CHANNEL_KEY).get() : channel;
            if (downstreamChannel != null) {
                InetSocketAddress downstreamAddress = (InetSocketAddress) downstreamChannel.remoteAddress();
                if (downstreamAddress != null) {
                    connectionMap.remove(downstreamAddress);
                    connectionStats.remove(downstreamAddress);
                    downstreamChannelMap.remove(downstreamAddress);
                }
            }
        }
    }

    public static class TrafficCounterHandler extends ChannelDuplexHandler {
        private final ConnectionStatistics stats;
        private final boolean downstreamChannel;
        private final LongAdder totalToUpstream;
        private final LongAdder totalToDownstream;

        public TrafficCounterHandler(ConnectionStatistics stats,
                                     boolean downstreamChannel,
                                     LongAdder totalToUpstream,
                                     LongAdder totalToDownstream) {
            this.stats = stats;
            this.downstreamChannel = downstreamChannel;
            this.totalToUpstream = totalToUpstream;
            this.totalToDownstream = totalToDownstream;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf buf) {
                int readableBytes = buf.readableBytes();
                if (downstreamChannel) {
                    stats.getToUpstreamBytes().add(readableBytes);
                    totalToUpstream.add(readableBytes);
                } else {
                    stats.getToDownstreamBytes().add(readableBytes);
                    totalToDownstream.add(readableBytes);
                }
                stats.setLastActivityAt();
            }
            super.channelRead(ctx, msg);
        }
    }


    @Override
    public void close() {
        sched.shutdown();
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        bossGroup.shutdownGracefully().syncUninterruptibly();
        workerGroup.shutdownGracefully().syncUninterruptibly();
    }

    @Override
    public long getEstablishedConnections() {
        return downstreamChannelMap.size();
    }

    @Override
    public BiMap<InetSocketAddress, InetSocketAddress> getDownstreamAddressAsKeyConnectionMap() {
        return ImmutableBiMap.copyOf(connectionMap);
    }

    @Override
    public BiMap<InetSocketAddress, InetSocketAddress> getProxyLAddressAsKeyConnectionMap() {
        return ImmutableBiMap.copyOf(connectionMap.inverse());

    }

    @Override
    public Map<InetSocketAddress, ConnectionStatistics> getDownstreamAddressAsKeyConnectionStats() {
        return Map.copyOf(connectionStats);
    }

    @Override
    public long getTotalToUpstream() {
        return totalToUpstream.sum();
    }

    @Override
    public long getTotalToDownstream() {
        return totalToDownstream.sum();
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
        return upstreamHost;
    }

    @Override
    public @Nullable InetSocketAddress translate(@Nullable InetSocketAddress nattedAddress) {
        return connectionMap.inverse().get(nattedAddress);
    }

    @Override
    public ForwarderIOHandlerType getForwarderIOHandlerType() {
        return ioHandler.ioHandlerType();
    }
}