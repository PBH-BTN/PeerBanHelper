package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.text.Lang;
import com.offbynull.portmapper.PortMapperFactory;
import com.offbynull.portmapper.gateway.Bus;
import com.offbynull.portmapper.gateways.network.NetworkGateway;
import com.offbynull.portmapper.gateways.network.internalmessages.KillNetworkRequest;
import com.offbynull.portmapper.gateways.process.ProcessGateway;
import com.offbynull.portmapper.gateways.process.internalmessages.KillProcessRequest;
import com.offbynull.portmapper.mapper.MappedPort;
import com.offbynull.portmapper.mapper.PortMapper;
import com.offbynull.portmapper.mapper.PortType;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public class PBHPortMapper {
    private final NetworkGateway network;
    private final ProcessGateway process;
    private final Bus networkBus;
    private final Bus processBus;
    private final Map<MappedPort, MappedPort> originalToRefreshedPortMap = Collections.synchronizedMap(new HashMap<>());
    private ScheduledExecutorService sched = Executors.newScheduledThreadPool(16, Thread.ofVirtual().factory());

    public PBHPortMapper() {
        this.network = NetworkGateway.create();
        this.process = ProcessGateway.create();
        this.networkBus = network.getBus();
        this.processBus = process.getBus();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                List<PortMapper> mappers = PortMapperFactory.discover(networkBus, processBus);
                if (mappers.isEmpty()) {
                    return;
                }
                var mapper = mappers.getFirst();
                var it = originalToRefreshedPortMap.entrySet().iterator();
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                while (it.hasNext()) {
                    var set = it.next();
                    futures.add(unmapPort(mapper, set.getKey()));
                    it.remove();
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            networkBus.send(new KillNetworkRequest());
            processBus.send(new KillProcessRequest());
        }));
    }

    public CompletableFuture<Void> unmapPort(PortMapper mapper, MappedPort mappedPort) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                mapper.unmapPort(originalToRefreshedPortMap.get(mappedPort));
                mapper.unmapPort(mappedPort);
                originalToRefreshedPortMap.remove(mappedPort);
            } catch (InterruptedException ignored) {
            }
            return null;
        });
    }

    @Nullable
    public CompletableFuture<MappedPort> mapPort(PortMapper mapper, PortType portType, int localPort) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    MappedPort mappedPort = mapper.mapPort(portType, localPort, localPort, Integer.MAX_VALUE);
                    originalToRefreshedPortMap.put(mappedPort, mappedPort);
                    sched.scheduleWithFixedDelay(() -> {
                        try {
                            var newMapperPort = mapper.refreshPort(mappedPort, Integer.MAX_VALUE);
                            originalToRefreshedPortMap.put(mappedPort, newMapperPort);
                        } catch (Exception e) {
                            log.error(tlUI(Lang.PORT_MAPPER_PORT_MAPPING_FAILED, mappedPort.getInternalPort(), mappedPort.getPortType().name()), e);
                        }
                    }, mappedPort.getLifetime() / 2, mappedPort.getLifetime() / 2, TimeUnit.SECONDS);
                    log.info(tlUI(Lang.PORT_MAPPER_PORT_MAPPED, mapper.getSourceAddress().getHostAddress(), mappedPort.getInternalPort(), mappedPort.getPortType().name(), mappedPort.getExternalPort(), mappedPort.getExternalAddress().getHostAddress(), mappedPort.getLifetime()));
                    return mappedPort;
                } catch (InterruptedException e) {
                    log.error("Unable to mapPort", e);
                    return null;
                }
            });
        } catch (Exception e) {
            log.error(tlUI(Lang.PORT_MAPPER_PORT_MAPPING_FAILED, mapper.getSourceAddress().getHostAddress(), localPort, portType.name()), e);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Nullable
    public PortMapper getPortMapper() {
        try {
            List<PortMapper> mappers = PortMapperFactory.discover(networkBus, processBus);
            if (mappers.isEmpty()) {
                return null;
            }
            return mappers.getFirst();
        } catch (InterruptedException e) {
            return null;
        }
    }
}
