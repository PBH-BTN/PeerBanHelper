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
import com.offbynull.portmapper.mappers.natpmp.NatPmpPortMapper;
import com.offbynull.portmapper.mappers.pcp.PcpPortMapper;
import com.offbynull.portmapper.mappers.upnpigd.UpnpIgdPortMapper;
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
public final class PBHPortMapperImpl implements PBHPortMapper {
    private final NetworkGateway network;
    private final ProcessGateway process;
    private final Bus networkBus;
    private final Bus processBus;
    private final Map<MappedPort, MappedPort> originalToRefreshedPortMap = Collections.synchronizedMap(new HashMap<>());
    private List<PortMapper> mappers = null;
    private final Object scanMappersLock = new Object();
    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(16, Thread.ofVirtual().factory());

    public PBHPortMapperImpl() {
        this.network = NetworkGateway.create();
        this.process = ProcessGateway.create();
        this.networkBus = network.getBus();
        this.processBus = process.getBus();
        Thread.ofPlatform().name("PortMapperScanner").start(this::scanMappers);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (originalToRefreshedPortMap.isEmpty()) {
                    return;
                }
                if (mappers == null || mappers.isEmpty()) {
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
            if (this.mappers != null) return;
            log.info(tlUI(Lang.PORTMAPPER_SCANNING));
            //this.mappers = PortMapperFactory.discover(networkBus, processBus);
            List<PortMapper> mapper = Collections.synchronizedList(new ArrayList<>());
            CompletableFuture<Void> scanNatPmp = CompletableFuture.runAsync(() -> {
                try {
                    mapper.addAll(NatPmpPortMapper.identify(networkBus, processBus));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, Executors.newVirtualThreadPerTaskExecutor());
            CompletableFuture<Void> scanUpnp = CompletableFuture.runAsync(() -> {
                try {
                    mapper.addAll(UpnpIgdPortMapper.identify(networkBus));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, Executors.newVirtualThreadPerTaskExecutor());
            CompletableFuture<Void> scanPCP = CompletableFuture.runAsync(() -> {
                try {
                    mapper.addAll(PcpPortMapper.identify(networkBus, processBus));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, Executors.newVirtualThreadPerTaskExecutor());
            scanNatPmp.join();
            scanUpnp.join();
            scanPCP.join();
            this.mappers = mapper;
            log.info(tlUI(Lang.PORTMAPPER_SCANNED, mappers.size()));
        }
    }

    @Override
    public List<PortMapper> getMappers() {
        if (mappers == null) {
            scanMappers();
        }
        return mappers;
    }

    @Override
    public CompletableFuture<Void> unmapPort(List<PortMapper> mappers, MappedPort mappedPort) {
        return CompletableFuture.supplyAsync(() -> {
            for (PortMapper mapper : mappers) {
                try {
                    var originalToRefresh = originalToRefreshedPortMap.get(mappedPort);
                    if (originalToRefresh != null) {
                        mapper.unmapPort(originalToRefresh);
                    }
                    mapper.unmapPort(mappedPort);
                    originalToRefreshedPortMap.remove(mappedPort);
                } catch (Exception ignored) {
                }
            }
            return null;
        });
    }

    @Override
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
            }, Executors.newVirtualThreadPerTaskExecutor());
        } catch (Exception e) {
            log.error(tlUI(Lang.PORT_MAPPER_PORT_MAPPING_FAILED, "N/A", localPort, portType.name()), e);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public @Nullable List<PortMapper> getPortMapper() {
        try {
            return PortMapperFactory.discover(networkBus, processBus);
        } catch (InterruptedException e) {
            return null;
        }
    }
}
