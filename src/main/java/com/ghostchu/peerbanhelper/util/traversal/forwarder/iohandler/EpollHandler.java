package com.ghostchu.peerbanhelper.util.traversal.forwarder.iohandler;

import com.ghostchu.peerbanhelper.util.traversal.forwarder.ForwarderIOHandlerType;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;

public class EpollHandler implements ForwarderIOHandler {
    @Override
    public ServerBootstrap apply(ServerBootstrap bootstrap) {
        return bootstrap
                .channel(EpollServerSocketChannel.class)
                .option(EpollChannelOption.SO_REUSEPORT, true);
    }

    @Override
    public ForwarderIOHandlerType ioHandlerType() {
        return ForwarderIOHandlerType.IO_URING;
    }

    @Override
    public IoHandlerFactory ioHandlerFactory() {
        return EpollIoHandler.newFactory();
    }

    @Override
    public Class<? extends ServerChannel> serverSocketChannelClass() {
        return EpollServerSocketChannel.class;
    }
}
