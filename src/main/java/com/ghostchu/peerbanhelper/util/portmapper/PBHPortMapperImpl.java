package com.ghostchu.peerbanhelper.util.portmapper;

import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;
import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public final class PBHPortMapperImpl implements PBHPortMapper {
    private final GatewayDiscover gatewayDiscover = new GatewayDiscover();
    private final Object discoverLock = new Object();
    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(16, Thread.ofVirtual().factory());
    private final List<MappedPort> mappedPorts = Collections.synchronizedList(new ArrayList<>());

    public PBHPortMapperImpl() {
        gatewayDiscover.setTimeout(10);
        Thread.ofPlatform().name("PortMapperScanner").start(this::scanMappers);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (GatewayDevice gatewayDevice : getGatewayDevices()) {
                    for (MappedPort mappedPort : mappedPorts) {
                        executor.submit(() -> {
                            try {
                                gatewayDevice.deletePortMapping(mappedPort.getExternalPort(), mappedPort.getProtocol().name());
                            } catch (IOException | SAXException e) {
                                log.debug("Unable to delete port mapping on shutdown", e);
                            }
                        });
                    }
                }

            }
        }));
    }

    private void scanMappers() {
        synchronized (discoverLock) {
            if (!gatewayDiscover.getAllGateways().isEmpty()) return;
            log.info(tlUI(Lang.PORTMAPPER_SCANNING));
            try {
                gatewayDiscover.setTimeout(1000 * 15);
                gatewayDiscover.discover();
                log.info(tlUI(Lang.PORTMAPPER_SCANNED, gatewayDiscover.getAllGateways().size()));
            } catch (IOException | SAXException | ParserConfigurationException e) {
                log.error("Unable to discover UPnP gateways", e);
            }
        }
    }

    @Override
    public Collection<GatewayDevice> getGatewayDevices() {
        if (gatewayDiscover.getValidGateway() == null) {
            scanMappers();
        }
        return List.copyOf(gatewayDiscover.getAllGateways().values());
    }

    @Override
    public CompletableFuture<@NotNull Boolean> mapPort(int port, Protocol protocol, String description) {
        return CompletableFuture.supplyAsync(() -> {
            var gateways = getGatewayDevices();
            if (gateways.isEmpty()) {
                log.debug("No UPnP gateways found, mapPort failed...");
                return false;
            }
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                AtomicBoolean anySuccess = new AtomicBoolean(false);
                for (GatewayDevice device : gateways) {
                    executor.submit(() -> {
                        try {
                            if (device.addPortMapping(port, port, device.getLocalAddress().getHostAddress(), protocol.name(), description)) {
                                log.info(tlUI(Lang.PORT_MAPPER_PORT_MAPPED_NEW, device.getLocalAddress(), port, protocol.name(), device.getExternalIPAddress(), device.getFriendlyName(), device.getManufacturer(), device.getModelName(), device.getModelNumber()));
                                anySuccess.set(true);
                            }
                        } catch (IOException | SAXException e) {
                            log.debug("Unable to add portMapping on port-mapping", e);
                        }
                    });
                    mappedPorts.add(new MappedPort(protocol, port));
                }
                return anySuccess.get();
            }
        });
    }

    @Override
    public CompletableFuture<@NotNull Boolean> unmapPort(int port, Protocol protocol) {
        return CompletableFuture.supplyAsync(() -> {
            var gateways = getGatewayDevices();
            if (gateways.isEmpty()) {
                log.debug("No UPnP gateways found, unmapPort failed...");
                return false;
            }
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                AtomicBoolean anySuccess = new AtomicBoolean(false);
                for (GatewayDevice device : gateways) {
                    executor.submit(() -> {
                        try {
                            if (device.deletePortMapping(port, protocol.name())) {
                                anySuccess.set(true);
                            }
                        } catch (IOException | SAXException e) {
                            log.debug("Unable to delete portMapping on port-mapping", e);
                        }
                    });
                    mappedPorts.add(new MappedPort(protocol, port));
                }
                return anySuccess.get();
            }
        });
    }


}
