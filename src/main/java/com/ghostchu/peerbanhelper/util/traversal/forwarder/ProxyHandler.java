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
    
    private Channel remoteChannel;
    
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
                log.debug("Connected to remote successfully {}:{}", remoteHost, remotePort);
            } else {
                log.error("Unable to connect to remote {}:{}", remoteHost, remotePort, channelFuture.cause());
                ctx.channel().close();
            }
        });
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (remoteChannel != null && remoteChannel.isActive()) {
            // 将客户端数据转发到远程服务器（上传）
            if (msg instanceof ByteBuf buf) {
                int bytes = buf.readableBytes();
                connectionInfo.addUploadBytes(bytes);
            }
            remoteChannel.writeAndFlush(msg);
        } else {
            // 如果远程连接未建立或已关闭，释放消息
            if (msg instanceof ByteBuf buf) {
                buf.release();
            }
        }
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.debug("Client disconnected: {}", connectionInfo.getClientAddress());
        if (remoteChannel != null && remoteChannel.isActive()) {
            remoteChannel.close();
        }
        forwarder.removeConnection(connectionInfo.getRemoteAddress().getHostString()+":"+connectionInfo.getRemoteAddress().getPort());
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("Exception in proxy handler: {}",connectionInfo.getRemoteAddress().getHostString()+":"+connectionInfo.getRemoteAddress().getPort(), cause);
        ctx.close();
    }
}
