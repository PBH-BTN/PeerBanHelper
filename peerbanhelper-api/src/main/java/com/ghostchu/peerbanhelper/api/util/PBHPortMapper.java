package com.ghostchu.peerbanhelper.api.util;

import com.offbynull.portmapper.mapper.MappedPort;
import com.offbynull.portmapper.mapper.PortMapper;
import com.offbynull.portmapper.mapper.PortType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface PBHPortMapper {
    List<PortMapper> getMappers();

    CompletableFuture<Void> unmapPort(List<PortMapper> mappers, MappedPort mappedPort);

    CompletableFuture<@Nullable MappedPort> mapPort(List<PortMapper> mappers, PortType portType, int localPort);

    @Nullable List<PortMapper> getPortMapper();
}
