package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.ExternalSwitch;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

//@Component
@Slf4j
public final class PBHPortMapper {
    private final NetworkGateway network;
    private final ProcessGateway process;
    private final Bus networkBus;
    private final Bus processBus;
    private final Map<MappedPort, MappedPort> originalToRefreshedPortMap = Collections.synchronizedMap(new HashMap<>());
    private List<PortMapper> mappers = null;
    private final Object scanMappersLock = new Object();
    private ScheduledExecutorService sched = Executors.newScheduledThreadPool(16, Thread.ofVirtual().factory());

    public PBHPortMapper() {
        this.network = NetworkGateway.create();
        this.process = ProcessGateway.create();
        this.networkBus = network.getBus();
        this.processBus = process.getBus();
        Thread.startVirtualThread(()->{
            scanMappers();
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if(originalToRefreshedPortMap.isEmpty()){
                    return;
                }
                if(mappers == null || mappers.isEmpty()){
                    return;
                }
                var it = originalToRefreshedPortMap.entrySet().iterator();
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                while (it.hasNext()) {
                    var set = it.next();
                    futures.add(unmapPort(mappers, set.getKey()));
                    it.remove();
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } finally {
                networkBus.send(new KillNetworkRequest());
                processBus.send(new KillProcessRequest());
                sched.shutdown();
            }
        }));
    }

    private void scanMappers() {
        synchronized (scanMappersLock) {
            try {
                this.mappers = PortMapperFactory.discover(networkBus, processBus);
            } catch (InterruptedException exception) {
                log.warn("Unable to lookup port mappers", exception);
            }
        }
    }

    public List<PortMapper> getMappers(){
        if(mappers == null){
            scanMappers();
        }
        return mappers;
    }

    public CompletableFuture<Void> unmapPort(List<PortMapper> mappers, MappedPort mappedPort) {
        return CompletableFuture.supplyAsync(() -> {
            for (PortMapper mapper : mappers) {
                try {
                    mapper.unmapPort(originalToRefreshedPortMap.get(mappedPort));
                    mapper.unmapPort(mappedPort);
                    originalToRefreshedPortMap.remove(mappedPort);
                } catch (InterruptedException ignored) {
                }
            }
            return null;
        });
    }

    public CompletableFuture<@Nullable MappedPort> mapPort(List<PortMapper> mappers, PortType portType, int localPort) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                log.info(tlUI(Lang.PORT_MAPPER_PORT_MAPPING, localPort, portType.name()));
                Map<PortMapper, MappedPort> mappedPorts = new LinkedHashMap<>();
                for (PortMapper mapper : mappers) {
                    try {
                        MappedPort mappedPort = mapper.mapPort(portType, localPort, localPort, 600);
                        originalToRefreshedPortMap.put(mappedPort, mappedPort);
                        sched.scheduleWithFixedDelay(() -> {
                            try {
                                var newMapperPort = mapper.refreshPort(mappedPort, 600);
                                originalToRefreshedPortMap.put(mappedPort, newMapperPort);
                            } catch (Exception e) {
                                if (ExternalSwitch.parse("pbh.portMapper.disableRefreshFailRetry", "false").equals("true")) {
                                    return;
                                }
                                log.error(tlUI(Lang.PORT_MAPPER_PORT_MAPPING_FAILED, mapper.getSourceAddress().getHostAddress(), mappedPort.getPortType().name(), mappedPort.getInternalPort()), e);
                                mapPort(mappers, portType, localPort);
                            }
                        }, mappedPort.getLifetime() / 2, mappedPort.getLifetime() / 2, TimeUnit.SECONDS);
                        mappedPorts.put(mapper, mappedPort);
                    } catch (Exception ignored) {

                    }
                }
                boolean anyExternal = false;
                for (var entry : mappedPorts.entrySet()) {
                    var inetAddress = entry.getValue().getExternalAddress();
                    if (!(inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress() || inetAddress.isSiteLocalAddress())) {
                        anyExternal = true;
                    }
                    var mappedPort = entry.getValue();
                    log.info(tlUI(Lang.PORT_MAPPER_PORT_MAPPED, entry.getKey().getSourceAddress().getHostAddress(), mappedPort.getInternalPort(), mappedPort.getPortType().name(), mappedPort.getExternalPort(), mappedPort.getExternalAddress().getHostAddress(), mappedPort.getLifetime()));
                }
                if (!anyExternal) {
                    log.warn(tlUI(Lang.PORT_MAPPER_PORT_MAPPED_BUT_INTERNAL_ADDRESS));
                }
                return mappedPorts.values().stream().findFirst().orElse(null);
            });
        } catch (Exception e) {
            log.error(tlUI(Lang.PORT_MAPPER_PORT_MAPPING_FAILED, "N/A", localPort, portType.name()), e);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Nullable
    public List<PortMapper> getPortMapper() {
        try {
            return PortMapperFactory.discover(networkBus, processBus);
        } catch (InterruptedException e) {
            return null;
        }
    }
}
