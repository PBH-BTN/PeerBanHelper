package com.ghostchu.peerbanhelper.util.portmapper;

import org.bitlet.weupnp.GatewayDevice;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface PBHPortMapper {
    Collection<GatewayDevice> getGatewayDevices();

    CompletableFuture<@NotNull Boolean> mapPort(int port, Protocol protocol, String description);

    CompletableFuture<@NotNull Boolean> unmapPort(int port, Protocol protocol);

}
