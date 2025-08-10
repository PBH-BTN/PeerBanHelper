package com.ghostchu.peerbanhelper.util.traversal.forwarder.iohandler;

import com.ghostchu.peerbanhelper.util.traversal.forwarder.ForwarderIOHandlerType;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.uring.IoUringChannelOption;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringServerSocketChannel;

public class IOUringHandler implements ForwarderIOHandler {
    @Override
    public ServerBootstrap apply(ServerBootstrap bootstrap) {
        return bootstrap
                .channel(IoUringServerSocketChannel.class)
                .option(IoUringChannelOption.SO_REUSEPORT, true);
    }

    @Override
    public ForwarderIOHandlerType ioHandlerType() {
        return ForwarderIOHandlerType.IO_URING;
    }

    @Override
    public IoHandlerFactory ioHandlerFactory() {
        return IoUringIoHandler.newFactory();
    }

    @Override
    public Class<? extends ServerChannel> serverSocketChannelClass() {
        return EpollServerSocketChannel.class;
    }
}
