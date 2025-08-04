package com.ghostchu.peerbanhelper.util.traversal;

import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;

public interface NatAddressProvider {
    @Nullable
    InetSocketAddress translate(@Nullable InetSocketAddress nattedAddress);
}
