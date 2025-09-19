package com.ghostchu.peerbanhelper.util.traversal.forwarder.iohandler;

import com.ghostchu.peerbanhelper.util.traversal.forwarder.ForwarderIOHandlerType;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.ServerChannel;

public interface ForwarderIOHandler {
    ServerBootstrap apply(ServerBootstrap bootstrap);

    ForwarderIOHandlerType ioHandlerType();

    IoHandlerFactory ioHandlerFactory();

    Class<? extends ServerChannel> serverSocketChannelClass();

    Class<? extends Channel> clientSocketChannelClass();
}
