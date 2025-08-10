package com.ghostchu.peerbanhelper.util.traversal.forwarder.iohandler;

import com.ghostchu.peerbanhelper.util.traversal.forwarder.ForwarderIOHandlerType;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.ServerChannel;
import io.netty.channel.kqueue.KQueueChannelOption;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;

public class KQueueHandler implements ForwarderIOHandler {
    @Override
    public ServerBootstrap apply(ServerBootstrap bootstrap) {
        return bootstrap
                .channel(KQueueServerSocketChannel.class)
                .option(KQueueChannelOption.SO_REUSEPORT, true);
    }

    @Override
    public ForwarderIOHandlerType ioHandlerType() {
        return ForwarderIOHandlerType.IO_URING;
    }

    @Override
    public IoHandlerFactory ioHandlerFactory() {
        return KQueueIoHandler.newFactory();
    }

    @Override
    public Class<? extends ServerChannel> serverSocketChannelClass() {
        return KQueueServerSocketChannel.class;
    }

    @Override
    public Class<? extends Channel> clientSocketChannelClass() {
        return KQueueSocketChannel.class;
    }
}
