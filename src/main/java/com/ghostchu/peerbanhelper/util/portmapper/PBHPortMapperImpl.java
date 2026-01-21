package com.ghostchu.peerbanhelper.util.portmapper;

import com.ghostchu.peerbanhelper.text.Lang;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public final class PBHPortMapperImpl implements PBHPortMapper {
    private GatewayDiscover gatewayDiscover = null;
    private final Object discoverLock = new Object();
    private final List<MappedPort> mappedPorts = Collections.synchronizedList(new ArrayList<>());
    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(1);
    private final List<String> lastCheckedNics = new ArrayList<>();
    private final Lock nicCheckChangeLock = new ReentrantLock();

    public PBHPortMapperImpl() {
        Thread.ofPlatform().name("PortMapperScanner").start(this::scanMappers);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (GatewayDevice gatewayDevice : getGatewayDevices()) {
                    for (MappedPort mappedPort : List.copyOf(mappedPorts)) {
                        executor.submit(() -> {
                            try {
                                gatewayDevice.deletePortMapping(mappedPort.getExternalPort(), mappedPort.getProtocol().name());
                            } catch (IOException | SAXException e) {
                                log.debug("Unable to delete port mapping on shutdown", e);
                                Sentry.captureException(e);
                            }
                        });
                    }
                }

            }
        }));
        sched.scheduleWithFixedDelay(this::detectNICChange, 5, 5, TimeUnit.SECONDS);
    }

    private void detectNICChange() {
        if (nicCheckChangeLock.tryLock()) {
            try {
                var currentNics = getAllNICs();
                if (!currentNics.equals(lastCheckedNics)) {
                    updateNICsList();
                    log.debug(tlUI(Lang.PORT_MAPPER_NIC_CHANGES_DETECTED));
                    Thread.ofPlatform().name("PortMapperScanner").start(this::scanMappers);
                }
            } finally {
                nicCheckChangeLock.unlock();
            }
        }
    }

    private void scanMappers() {
        synchronized (discoverLock) {
            if(gatewayDiscover != null ){
                return;
            }
            log.info(tlUI(Lang.PORTMAPPER_SCANNING));
            try {
                updateNICsList();
                gatewayDiscover = new GatewayDiscover();
                gatewayDiscover.setTimeout(1000 * 15);
                gatewayDiscover.discover();
                log.info(tlUI(Lang.PORTMAPPER_SCANNED, gatewayDiscover.getAllGateways().size()));
            } catch (IOException | SAXException | ParserConfigurationException e) {
                log.error(tlUI(Lang.PORT_MAPPER_DISCOVER_IGD_FAILED), e);
                log.error("Unable to discover UPnP gateways", e);
                Sentry.captureException(e);
            }
        }
    }

    private void updateNICsList() {
        synchronized (lastCheckedNics) {
            lastCheckedNics.clear();
            lastCheckedNics.addAll(getAllNICs());
        }
    }

    private List<String> getAllNICs() {
        try {
            Enumeration<NetworkInterface> it = NetworkInterface.getNetworkInterfaces();
            List<String> currentNics = new ArrayList<>();
            it.asIterator()
                    .forEachRemaining(nic -> {
                        try {
                            if (!nic.isUp() || nic.isLoopback() || nic.isVirtual() || nic.isPointToPoint()) return;
                            nic.getInetAddresses().asIterator().forEachRemaining(inetAddress -> currentNics.add(inetAddress.getHostAddress()));
                        } catch (SocketException e) {
                            log.debug("Unable to process nics", e);
                        }

                    });
            Collections.sort(currentNics);
            return currentNics;
        } catch (SocketException e) {
            log.debug("Unable to update network interfaces collection", e);
            return new ArrayList<>(); // must modifiable
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
        }, Executors.newVirtualThreadPerTaskExecutor());
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
        }, Executors.newVirtualThreadPerTaskExecutor());
    }


}
