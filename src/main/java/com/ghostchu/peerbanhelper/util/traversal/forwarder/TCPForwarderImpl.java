package com.ghostchu.peerbanhelper.util.traversal.forwarder;

import com.ghostchu.peerbanhelper.BanList;
import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.banwave.PeerBanEvent;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.ipdb.IPDBManager;
import com.ghostchu.peerbanhelper.util.traversal.NatAddressProvider;
import com.ghostchu.peerbanhelper.util.traversal.forwarder.iohandler.*;
import com.ghostchu.peerbanhelper.util.traversal.forwarder.table.ConnectionStatistics;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class TCPForwarderImpl implements AutoCloseable, Forwarder, NatAddressProvider {
    private final int proxyPort;
    private final String proxyHost;
    private final String upstreamHost;
    private final int upstreamPort;
    private final BanList banList;

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("TCPForwarder-Cleanup").factory()
    );

    private final BiMap<InetSocketAddress, InetSocketAddress> connectionMap = Maps.synchronizedBiMap(HashBiMap.create());
    private final Map<InetSocketAddress, ConnectionStatistics> connectionStats = new ConcurrentHashMap<>();
    private final Map<InetSocketAddress, Channel> downstreamChannelMap = new ConcurrentHashMap<>();
    private final Set<InetAddress> pendingConnectionIps = ConcurrentHashMap.newKeySet();

    private final LongAdder connectionHandled = new LongAdder();
    private final LongAdder connectionFailed = new LongAdder();
    private final LongAdder connectionBlocked = new LongAdder();
    private final LongAdder connectionRejected = new LongAdder();

    private final LongAdder totalToUpstream = new LongAdder();
    private final LongAdder totalToDownstream = new LongAdder();
    private final IPDBManager ipdb;

    private Channel serverChannel;
    private final ForwarderIOHandler ioHandler;

    public TCPForwarderImpl(BanList banList, String proxyHost, int proxyPort, String upstreamHost, int upstreamPort, IPDBManager ipdb) {
        this.banList = banList;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.upstreamHost = upstreamHost;
        this.upstreamPort = upstreamPort;
        this.ipdb = ipdb;

        if (IoUring.isAvailable()) { // 性能最好
            ioHandler = new IOUringHandler();
        } else if (Epoll.isAvailable()) { // 性能很不错！
            ioHandler = new EpollHandler();
        } else if (KQueue.isAvailable()) { // FreeBSD/MacOS
            ioHandler = new KQueueHandler();
        } else { // oh shit
            ioHandler = new NioHandler();
        }

        log.info("IOHandler selected: {}", ioHandler.getClass().getSimpleName());
        this.bossGroup = new MultiThreadIoEventLoopGroup(ioHandler.ioHandlerFactory());
        this.workerGroup = new MultiThreadIoEventLoopGroup(ioHandler.ioHandlerFactory());
        log.debug("Netty TCPForwarder created: proxy {}:{}, upstream {}:{}", proxyHost, proxyPort, upstreamHost, upstreamPort);

        sched.scheduleAtFixedRate(this::cleanupBannedConnections, 0, 30, TimeUnit.SECONDS);
        Main.getEventBus().register(this);
    }

    @Override
    public void start() {
        ServerBootstrap b = new ServerBootstrap();
        ioHandler.apply(
                        b.group(bossGroup, workerGroup)
                                .channel(ioHandler.serverSocketChannelClass())
                                .childHandler(new ChannelInitializer<SocketChannel>() {
                                    @Override
                                    protected void initChannel(SocketChannel ch) {
                                        ch.pipeline().addLast(new ProxyFrontendHandler());
                                    }
                                })
                )
                .option(ChannelOption.SO_BACKLOG, 128)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.AUTO_READ, false); // 在连接到上游之前，暂停读取

        try {
            serverChannel = b.bind(proxyHost, proxyPort).sync().channel();
            log.debug("Netty TCPForwarder started and listening on {}:{}", proxyHost, proxyPort);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(tlUI(Lang.AUTOSTUN_TCP_FORWARDER_UNABLE_START), e);
            close();
        }
    }

    @Subscribe
    public void onPeerBanned(PeerBanEvent peerBanEvent) {
        var bannedPeerAddr = peerBanEvent.getPeer().getAddress();
        log.debug("Received PeerBanEvent for {}", bannedPeerAddr);

        downstreamChannelMap.forEach((address, channel) -> {
            var inetAddress = address.getAddress();
            if (inetAddress == null) {
                return;
            }
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
        downstreamChannelMap.forEach((clientAddress, channel) -> {
            InetAddress inetAddress = clientAddress.getAddress();
            if (inetAddress == null) {
                return;
            }
            IPAddress clientIp = IPAddressUtil.getIPAddress(inetAddress.getHostAddress());
            if (banList.contains(clientIp)) {
                log.debug("Closing connection from banned address during cleanup: {}", clientAddress);
                closeConnectionByDownstreamAddress(clientAddress);
                connectionBlocked.increment();
            }
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
            if (downstreamSocketAddress == null || downstreamSocketAddress.getAddress() == null) {
                log.debug("Reject connection with unresolved remote address: {}", downstreamSocketAddress);
                downstreamChannel.close();
                connectionRejected.increment();
                return;
            }

            if (isDuplicateConnection(downstreamSocketAddress)) {
                log.debug("Multiple connections from the same IP address detected: {}, disconnecting...", downstreamSocketAddress.getAddress().getHostAddress());
                downstreamChannel.close();
                connectionRejected.increment();
                return;
            }

            if (!reservePendingConnection(downstreamSocketAddress)) {
                log.debug("Connection from the same IP is already pending or established: {}, disconnecting...", downstreamSocketAddress.getAddress().getHostAddress());
                downstreamChannel.close();
                connectionRejected.increment();
                return;
            }
            // --- Ban Check ---
            IPAddress downstreamIpAddress = IPAddressUtil.getIPAddress(downstreamSocketAddress.getAddress().getHostAddress());
            if (ifBannedAddress(downstreamIpAddress)) {
                releasePendingConnection(downstreamSocketAddress);
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

            connectToUpstreamFriendly(b, downstreamSocketAddress, future -> {
                try {
                    if (future.isSuccess()) {
                        // --- Friendly Address Binding Logic ---
                        final Channel upstreamChannel = future.channel();
                        log.debug("Connected to upstream: {}, starting dual direction forward channel", upstreamChannel.remoteAddress());

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
                        stats.setIpGeoData(ipdb.queryIPDB(downstreamSocketAddress.getAddress()).geoData().get());
                        connectionStats.put(downstreamSocketAddress, stats);
                        downstreamChannelMap.put(downstreamSocketAddress, downstreamChannel);

                        // 添加统计处理器
                        downstreamChannel.pipeline().addFirst(new TrafficCounterHandler(stats, true, totalToUpstream, totalToDownstream));
                        upstreamChannel.pipeline().addFirst(new TrafficCounterHandler(stats, false, totalToUpstream, totalToDownstream));

                        // 移除当前处理器
                        ctx.pipeline().remove(ProxyFrontendHandler.this);
                        // 全部设置完毕，开始读取下游数据
                        downstreamChannel.config().setAutoRead(true);
                    } else {
                        log.debug("Error connecting to upstream server", future.cause());
                        downstreamChannel.close();
                        connectionFailed.increment();
                        if (connectionFailed.sum() % 50 == 0) {
                            log.error(tlUI(Lang.AUTOSTUN_TCP_FORWARDER_UNABLE_CONNECT_UPSTREAM, upstreamHost + ":" + upstreamPort, connectionFailed.sum()), future.cause());
                        }
                    }
                } finally {
                    releasePendingConnection(downstreamSocketAddress);
                }
            });
        }

        private boolean ifBannedAddress(IPAddress downstreamIpAddress) {
            return banList.contains(downstreamIpAddress);
        }

        private void connectToUpstreamFriendly(Bootstrap b,
                                               InetSocketAddress downstreamSocket,
                                               ChannelFutureListener completion) {
            InetAddress incomingAddress = downstreamSocket.getAddress();
            InetSocketAddress upstreamAddress = new InetSocketAddress(upstreamHost, upstreamPort);
            InetAddress upstreamInetAddress = upstreamAddress.getAddress();

            boolean useFriendly =
                    upstreamInetAddress != null
                            && upstreamInetAddress.isLoopbackAddress()
                            && incomingAddress instanceof Inet4Address
                            && ExternalSwitch.parseBoolean("pbh.TCPForwarder.useFriendlyAddressForLoopback", true);

            if (useFriendly) {
                byte[] incomingBytes = incomingAddress.getAddress();
                if (incomingBytes != null && incomingBytes.length >= 4) {
                    try {
                        byte[] bytes = new byte[4];
                        bytes[0] = 127;
                        bytes[1] = incomingBytes[1];
                        bytes[2] = incomingBytes[2];
                        bytes[3] = incomingBytes[3];

                        InetAddress outgoingAddress = InetAddress.getByAddress(bytes);

                        Bootstrap first = b.clone();
                        first.localAddress(outgoingAddress, downstreamSocket.getPort())
                                .connect(upstreamAddress)
                                .addListener((ChannelFuture future) -> {
                                    if (future.isSuccess()) {
                                        completion.operationComplete(future);
                                    } else {
                                        // 尝试绑定到友好地址和原始端口
                                        log.debug("Failed to bind to friendly address {}:{}, trying random port.",
                                                outgoingAddress.getHostAddress(), downstreamSocket.getPort(), future.cause());

                                        // 失败则退回，绑定到友好地址和随机端口
                                        Bootstrap second = b.clone();
                                        second.localAddress(outgoingAddress, 0)
                                                .connect(upstreamAddress)
                                                .addListener((ChannelFuture future2) -> {
                                                    if (future2.isSuccess()) {
                                                        completion.operationComplete(future2);
                                                    } else {
                                                        log.debug("Failed to bind to friendly address {} with random port as well.",
                                                                outgoingAddress.getHostAddress(), future2.cause());

                                                        Bootstrap fallback = b.clone();
                                                        fallback.connect(upstreamAddress)
                                                                .addListener(completion);
                                                    }
                                                });
                                    }
                                });
                        return;
                    } catch (UnknownHostException e) {
                        // fall through to default
                    }
                }
            }

            Bootstrap fallback = b.clone();
            log.debug("Failed to bind to friendly address, falling back to default.");
            fallback.connect(upstreamAddress).addListener(completion);
        }

        private boolean reservePendingConnection(InetSocketAddress downstreamSocketAddress) {
            InetAddress remoteInetAddress = downstreamSocketAddress.getAddress();
            return remoteInetAddress != null && pendingConnectionIps.add(remoteInetAddress);
        }

        private void releasePendingConnection(@Nullable InetSocketAddress downstreamSocketAddress) {
            if (downstreamSocketAddress == null) {
                return;
            }
            InetAddress remoteInetAddress = downstreamSocketAddress.getAddress();
            if (remoteInetAddress != null) {
                pendingConnectionIps.remove(remoteInetAddress);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            InetSocketAddress downstreamSocketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
            releasePendingConnection(downstreamSocketAddress);

            if (!(cause instanceof IOException)) {
                log.debug("Exception in ProxyFrontendHandler", cause);
            }
            ctx.close();
            connectionFailed.increment();
        }
    }

    private boolean isDuplicateConnection(InetSocketAddress downstreamSocketAddress) {
        InetAddress remoteInetAddress = downstreamSocketAddress.getAddress();
        if (remoteInetAddress == null) {
            return false;
        }
        byte[] target = remoteInetAddress.getAddress();

        synchronized (connectionMap) {
            for (InetSocketAddress inetSocketAddress : connectionMap.keySet()) {
                InetAddress existingAddress = inetSocketAddress.getAddress();
                if (existingAddress != null && Arrays.equals(target, existingAddress.getAddress())) {
                    return true;
                }
            }
        }
        return pendingConnectionIps.contains(remoteInetAddress);
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
            if (!(cause instanceof IOException)) {
                log.debug("Exception in RelayHandler from {}", ctx.channel().remoteAddress(), cause);
            }
            ctx.close();
            if (relayChannel.isActive()) {
                relayChannel.close();
            }
        }

        private void cleanupConnectionState(Channel channel) {
            Channel downstreamChannel = channel;

            // 如果当前关闭的是上游，则优先切回下游；如果当前关闭的就是下游，则直接使用当前 channel。
            if (!downstreamChannelMap.containsValue(channel) && channel.hasAttr(RELAY_CHANNEL_KEY)) {
                Channel peer = channel.attr(RELAY_CHANNEL_KEY).get();
                if (peer != null && downstreamChannelMap.containsValue(peer)) {
                    downstreamChannel = peer;
                }
            }

            InetSocketAddress downstreamAddress = (InetSocketAddress) downstreamChannel.remoteAddress();
            if (downstreamAddress != null) {
                connectionMap.remove(downstreamAddress);
                connectionStats.remove(downstreamAddress);
                downstreamChannelMap.remove(downstreamAddress);
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
        synchronized (connectionMap) {
            return ImmutableBiMap.copyOf(connectionMap);
        }
    }

    @Override
    public BiMap<InetSocketAddress, InetSocketAddress> getProxyLAddressAsKeyConnectionMap() {
        synchronized (connectionMap) {
            return ImmutableBiMap.copyOf(connectionMap.inverse());
        }
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
    public long getConnectionRejected() {
        return connectionRejected.sum();
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
        synchronized (connectionMap) {
            return connectionMap.inverse().get(nattedAddress);
        }
    }

    @Override
    public ForwarderIOHandlerType getForwarderIOHandlerType() {
        return ioHandler.ioHandlerType();
    }
}