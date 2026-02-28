package com.ghostchu.peerbanhelper.util.portmapper;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import com.sshtools.porter.UPnP;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

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
    private UPnP.Discovery gatewayDiscover = null;
    private final Object discoverLock = new Object();
    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(1);
    private final List<String> lastCheckedNics = new ArrayList<>();
    private final Lock nicCheckChangeLock = new ReentrantLock();

    public PBHPortMapperImpl() {
        sched.scheduleWithFixedDelay(this::detectNICChange, 0, 30, TimeUnit.SECONDS);
    }

    private void detectNICChange() {
        if (nicCheckChangeLock.tryLock()) {
            try {
                var currentNics = getAllNICs();
                if (!currentNics.equals(lastCheckedNics)) {
                    updateNICsList();
                    log.debug(tlUI(Lang.PORT_MAPPER_NIC_CHANGES_DETECTED));
                    Thread.ofVirtual().name("PortMapperScanner").start(this::scanMappers);
                }
            } finally {
                nicCheckChangeLock.unlock();
            }
        }
    }

    private void scanMappers() {
        synchronized (discoverLock) {
            if (!Main.getMainConfig().getBoolean("auto-stun.enabled", false)) {
                return;
            }
            log.info(tlUI(Lang.PORTMAPPER_SCANNING));
            updateNICsList();
            if (gatewayDiscover != null) {
                gatewayDiscover.close();
            }
            gatewayDiscover = new UPnP.DiscoveryBuilder().withSoTimeout(1000 * 6).build();
            log.info(tlUI(Lang.PORTMAPPER_SCANNED, gatewayDiscover.gateways().size()));
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
    public Collection<UPnP.Gateway> getGatewayDevices() {
        if (gatewayDiscover == null) {
            return List.of();
        }
        return gatewayDiscover.gateways();
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
                for (UPnP.Gateway device : gateways) {
                    executor.submit(() -> {
                        if (device.map(port, port, protocol.name(), description)) {
                            log.info(tlUI(Lang.PORT_MAPPER_PORT_MAPPED_NEW, device.localIP(), port, protocol.name(), device.ip()));
                            anySuccess.set(true);
                        }
                    });
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
                for (UPnP.Gateway device : gateways) {
                    executor.submit(() -> {
                        if (device.unmap(port, protocol.name())) {
                            anySuccess.set(true);
                        }
                    });
                }
                return anySuccess.get();
            }
        }, Executors.newVirtualThreadPerTaskExecutor());
    }


}
