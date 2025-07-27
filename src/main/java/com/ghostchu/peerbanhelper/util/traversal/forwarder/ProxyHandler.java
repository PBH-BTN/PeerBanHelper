package com.ghostchu.peerbanhelper.util.traversal.forwarder;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

/**
 * 处理客户端连接的处理器
 */
@Slf4j
public class ProxyHandler extends ChannelInboundHandlerAdapter {
    
    private final String remoteHost;
    private final int remotePort;
    private final SocketForwarder forwarder;
    private final ConnectionInfo connectionInfo;
    
    private volatile Channel remoteChannel;
    private volatile boolean remoteConnected = false;
    
    public ProxyHandler(String remoteHost, int remotePort, SocketForwarder forwarder, ConnectionInfo connectionInfo) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.forwarder = forwarder;
        this.connectionInfo = connectionInfo;
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // 当客户端连接激活时，建立到远程服务器的连接
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000) // 10秒连接超时
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new RemoteHandler(ctx.channel(), connectionInfo));
                    }
                });
        
        ChannelFuture future = bootstrap.connect(remoteHost, remotePort);
        remoteChannel = future.channel();
        
        future.addListener((ChannelFutureListener) channelFuture -> {
            if (channelFuture.isSuccess()) {
                remoteConnected = true;
                log.debug("Connected to remote successfully {}:{}", remoteHost, remotePort);
                // 连接成功后，触发读取操作
                ctx.channel().config().setAutoRead(true);
            } else {
                log.error("Unable to connect to remote {}:{}", remoteHost, remotePort, channelFuture.cause());
                remoteConnected = false;
                ctx.channel().close();
            }
        });
        
        // 暂停自动读取，等待远程连接建立
        ctx.channel().config().setAutoRead(false);
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (remoteConnected && remoteChannel != null && remoteChannel.isActive()) {
            // 将客户端数据转发到远程服务器（上传）
            if (msg instanceof ByteBuf buf) {
                int bytes = buf.readableBytes();
                connectionInfo.addUploadBytes(bytes);
            }
            remoteChannel.writeAndFlush(msg);
        } else {
            // 如果远程连接未建立或已关闭，释放消息并关闭客户端连接
            if (msg instanceof ByteBuf buf) {
                buf.release();
            }
            log.warn("Remote connection not ready, closing client connection: {}", connectionInfo.getClientAddress());
            ctx.channel().close();
        }
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.debug("Client disconnected: {}", connectionInfo.getClientAddress());
        remoteConnected = false;
        if (remoteChannel != null && remoteChannel.isActive()) {
            remoteChannel.close();
        }
        forwarder.removeConnection(connectionInfo.getRemoteAddress().getHostString()+":"+connectionInfo.getRemoteAddress().getPort());
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 对于常见的网络连接重置异常，使用debug级别日志
        if (cause instanceof java.net.SocketException && 
            cause.getMessage() != null && 
            (cause.getMessage().contains("Connection reset") || 
             cause.getMessage().contains("Connection aborted"))) {
            log.debug("Client connection reset: {}", connectionInfo.getClientAddress());
        } else {
            log.debug("Exception in proxy handler: {}", connectionInfo.getRemoteAddress().getHostString()+":"+connectionInfo.getRemoteAddress().getPort(), cause);
        }
        remoteConnected = false;
        ctx.close();
    }
}
