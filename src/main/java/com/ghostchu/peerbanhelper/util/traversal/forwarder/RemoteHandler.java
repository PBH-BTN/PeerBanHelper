package com.ghostchu.peerbanhelper.util.traversal.forwarder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * 处理远程服务器响应的处理器
 */
@Slf4j
public class RemoteHandler extends ChannelInboundHandlerAdapter {
    
    private final Channel clientChannel;
    private final ConnectionInfo connectionInfo;
    
    public RemoteHandler(Channel clientChannel, ConnectionInfo connectionInfo) {
        this.clientChannel = clientChannel;
        this.connectionInfo = connectionInfo;
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (clientChannel.isActive()) {
            if (msg instanceof ByteBuf buf) {
                int bytes = buf.readableBytes();
                connectionInfo.addDownloadBytes(bytes);
            }
            clientChannel.writeAndFlush(msg);
        } else {
            if (msg instanceof ByteBuf buf) {
                buf.release();
            }
            log.debug("Client channel inactive, closing remote connection: {}", connectionInfo.getRemoteAddress());
            ctx.close();
        }
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.debug("Remote disconnected: {}", connectionInfo.getRemoteAddress());
        if (clientChannel.isActive()) {
            clientChannel.close();
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 对于常见的网络连接重置异常，使用debug级别日志
        if (cause instanceof java.net.SocketException && 
            cause.getMessage() != null && 
            (cause.getMessage().contains("Connection reset") || 
             cause.getMessage().contains("Connection aborted"))) {
            log.debug("Remote connection reset: {}", connectionInfo.getRemoteAddress());
        } else {
            log.error("Remote handler exception: {}", connectionInfo.getRemoteAddress(), cause);
        }
        
        // 确保客户端连接也被关闭
        if (clientChannel.isActive()) {
            clientChannel.close();
        }
        ctx.close();
    }
}
