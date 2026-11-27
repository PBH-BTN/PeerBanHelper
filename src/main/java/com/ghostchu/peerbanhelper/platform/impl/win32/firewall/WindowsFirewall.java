package com.ghostchu.peerbanhelper.platform.impl.win32.firewall;

import com.ghostchu.peerbanhelper.platform.firewall.Firewall;
import com.ghostchu.peerbanhelper.platform.firewall.FirewallAddresses;
import com.ghostchu.peerbanhelper.platform.firewall.FirewallBackend;
import com.ghostchu.peerbanhelper.platform.firewall.FirewallCapabilities;
import com.ghostchu.peerbanhelper.platform.firewall.FirewallCrashCleanup;
import com.ghostchu.peerbanhelper.platform.firewall.FirewallException;
import com.ghostchu.peerbanhelper.platform.firewall.UnsupportedFirewall;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Process-scoped Windows Filtering Platform firewall.
 */
@Slf4j
public final class WindowsFirewall implements Firewall {
    private final ReentrantLock lock = new ReentrantLock();
    private final WfpNamespace namespace = new WfpNamespace();
    private final UUID providerKey = UUID.randomUUID();
    private final UUID subLayerKey = UUID.randomUUID();
    private final WfpNative nativeApi;
    private final FirewallCapabilities capabilities;
    private Map<InetAddress, List<Long>> filterIds = new LinkedHashMap<>();
    private boolean closed;

    private WindowsFirewall() {
        this.nativeApi = new WfpNative(namespace.name("dynamic session"));
        try {
            nativeApi.addProviderAndSublayer(
                    providerKey,
                    subLayerKey,
                    namespace.name("provider"),
                    namespace.name("sublayer")
            );
        } catch (Throwable throwable) {
            try {
                nativeApi.close();
            } catch (FirewallException cleanupFailure) {
                throwable.addSuppressed(cleanupFailure);
            }
            throw throwable;
        }
        this.capabilities = capabilities(true,
                "WFP system-wide filtering in a dynamic session; BFE removes objects when the session ends");
    }

    public static Firewall create() {
        if (!WindowsPrivileges.isElevated()) {
            return new UnsupportedFirewall(capabilities(false,
                    "Windows Filtering Platform mutation requires an elevated PeerBanHelper process"));
        }
        try {
            return new WindowsFirewall();
        } catch (Throwable throwable) {
            String detail = "Windows Filtering Platform backend unavailable: " + throwable.getMessage();
            log.debug("Unable to initialize Windows Filtering Platform backend", throwable);
            return new UnsupportedFirewall(capabilities(false, detail));
        }
    }

    @Override
    public @NotNull FirewallCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public @NotNull Set<InetAddress> addresses() {
        lock.lock();
        try {
            requireOpen();
            return FirewallAddresses.copyOf(filterIds.keySet());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void add(@NotNull Collection<? extends InetAddress> addresses) {
        Set<InetAddress> requested = FirewallAddresses.normalize(addresses);
        lock.lock();
        try {
            requireOpen();
            LinkedHashSet<InetAddress> added = new LinkedHashSet<>(requested);
            added.removeAll(filterIds.keySet());
            if (added.isEmpty()) {
                return;
            }
            addTransactional(added);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void remove(@NotNull Collection<? extends InetAddress> addresses) {
        Set<InetAddress> requested = FirewallAddresses.normalize(addresses);
        lock.lock();
        try {
            requireOpen();
            LinkedHashSet<InetAddress> removed = new LinkedHashSet<>(requested);
            removed.retainAll(filterIds.keySet());
            if (removed.isEmpty()) {
                return;
            }
            removeTransactional(removed);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void replace(@NotNull Collection<? extends InetAddress> addresses) {
        Set<InetAddress> requested = FirewallAddresses.normalize(addresses);
        lock.lock();
        try {
            requireOpen();
            LinkedHashSet<InetAddress> added = new LinkedHashSet<>(requested);
            added.removeAll(filterIds.keySet());
            LinkedHashSet<InetAddress> removed = new LinkedHashSet<>(filterIds.keySet());
            removed.removeAll(requested);
            if (added.isEmpty() && removed.isEmpty()) {
                return;
            }

            nativeApi.beginTransaction();
            Map<InetAddress, List<Long>> addedIds = new LinkedHashMap<>();
            boolean committed = false;
            try {
                deletedFilters(removed);
                addedIds.putAll(createFilters(added));
                nativeApi.commitTransaction();
                committed = true;
            } finally {
                if (!committed) {
                    nativeApi.abortTransactionQuietly();
                }
            }

            removed.forEach(filterIds::remove);
            filterIds.putAll(addedIds);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            requireOpen();
            if (filterIds.isEmpty()) {
                return;
            }
            removeTransactional(new LinkedHashSet<>(filterIds.keySet()));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            FirewallException failure = null;
            try {
                nativeApi.beginTransaction();
                boolean committed = false;
                try {
                    deletedFilters(new LinkedHashSet<>(filterIds.keySet()));
                    nativeApi.deleteProviderAndSublayer(providerKey, subLayerKey);
                    nativeApi.commitTransaction();
                    committed = true;
                } finally {
                    if (!committed) {
                        nativeApi.abortTransactionQuietly();
                    }
                }
            } catch (FirewallException exception) {
                failure = exception;
            } finally {
                filterIds = new LinkedHashMap<>();
                try {
                    nativeApi.close();
                } catch (FirewallException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
            if (failure != null) {
                throw new FirewallException(
                        "Unable to remove the Windows firewall namespace; dynamic session teardown was attempted",
                        failure
                );
            }
        } finally {
            lock.unlock();
        }
    }

    private void addTransactional(Collection<InetAddress> addresses) {
        nativeApi.beginTransaction();
        Map<InetAddress, List<Long>> addedIds = new LinkedHashMap<>();
        boolean committed = false;
        try {
            addedIds.putAll(createFilters(addresses));
            nativeApi.commitTransaction();
            committed = true;
        } finally {
            if (!committed) {
                nativeApi.abortTransactionQuietly();
            }
        }
        filterIds.putAll(addedIds);
    }

    private void removeTransactional(Collection<InetAddress> addresses) {
        nativeApi.beginTransaction();
        boolean committed = false;
        try {
            deletedFilters(addresses);
            nativeApi.commitTransaction();
            committed = true;
        } finally {
            if (!committed) {
                nativeApi.abortTransactionQuietly();
            }
        }
        addresses.forEach(filterIds::remove);
    }

    private Map<InetAddress, List<Long>> createFilters(Collection<InetAddress> addresses) {
        Map<InetAddress, List<Long>> added = new LinkedHashMap<>();
        for (InetAddress address : addresses) {
            List<Long> ids = new ArrayList<>(2);
            ids.add(nativeApi.addFilter(
                    providerKey,
                    subLayerKey,
                    outboundLayer(address),
                    address,
                    namespace.name("outbound " + address.getHostAddress())
            ));
            ids.add(nativeApi.addFilter(
                    providerKey,
                    subLayerKey,
                    inboundLayer(address),
                    address,
                    namespace.name("inbound " + address.getHostAddress())
            ));
            added.put(address, List.copyOf(ids));
        }
        return added;
    }

    private void deletedFilters(Collection<InetAddress> addresses) {
        for (InetAddress address : addresses) {
            for (long filterId : filterIds.getOrDefault(address, List.of())) {
                nativeApi.deleteFilter(filterId);
            }
        }
    }

    private UUID outboundLayer(InetAddress address) {
        if (address instanceof Inet4Address) {
            return WfpStructures.LAYER_ALE_AUTH_CONNECT_V4;
        }
        if (address instanceof Inet6Address) {
            return WfpStructures.LAYER_ALE_AUTH_CONNECT_V6;
        }
        throw new IllegalArgumentException("Unsupported address type: " + address);
    }

    private UUID inboundLayer(InetAddress address) {
        if (address instanceof Inet4Address) {
            return WfpStructures.LAYER_ALE_AUTH_RECV_ACCEPT_V4;
        }
        if (address instanceof Inet6Address) {
            return WfpStructures.LAYER_ALE_AUTH_RECV_ACCEPT_V6;
        }
        throw new IllegalArgumentException("Unsupported address type: " + address);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Firewall is closed");
        }
    }

    private static FirewallCapabilities capabilities(boolean available, String detail) {
        return new FirewallCapabilities(
                FirewallBackend.WINDOWS_FILTERING_PLATFORM,
                available,
                true,
                true,
                true,
                true,
                true,
                false,
                true,
                FirewallCrashCleanup.DYNAMIC_SESSION,
                detail
        );
    }
}
