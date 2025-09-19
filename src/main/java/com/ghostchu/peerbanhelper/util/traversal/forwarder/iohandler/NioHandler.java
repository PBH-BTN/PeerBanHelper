package com.ghostchu.peerbanhelper.util.traversal.forwarder.iohandler;

import com.ghostchu.peerbanhelper.util.traversal.forwarder.ForwarderIOHandlerType;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioChannelOption;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NioHandler implements ForwarderIOHandler {
    @Override
    public ServerBootstrap apply(ServerBootstrap bootstrap) {
        if (NioChannelOption.exists("SO_REUSEPORT")) {
            try {
                bootstrap = bootstrap.option(NioChannelOption.valueOf("SO_REUSEPORT"), true);
            } catch (Exception ignored) {
                log.debug("NioChannelOption SO_REUSEPORT not supported, ignoring.");
            }
        }
        return bootstrap;
    }

    @Override
    public ForwarderIOHandlerType ioHandlerType() {
        return ForwarderIOHandlerType.IO_URING;
    }

    @Override
    public IoHandlerFactory ioHandlerFactory() {
        return NioIoHandler.newFactory();
    }

    @Override
    public Class<? extends ServerChannel> serverSocketChannelClass() {
        return NioServerSocketChannel.class;
    }

    @Override
    public Class<? extends Channel> clientSocketChannelClass() {
        return NioSocketChannel.class;
    }
}
