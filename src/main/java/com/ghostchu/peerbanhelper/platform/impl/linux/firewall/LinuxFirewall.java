package com.ghostchu.peerbanhelper.platform.impl.linux.firewall;

import com.ghostchu.peerbanhelper.platform.firewall.Firewall;
import com.ghostchu.peerbanhelper.platform.firewall.FirewallAddresses;
import com.ghostchu.peerbanhelper.platform.firewall.FirewallBackend;
import com.ghostchu.peerbanhelper.platform.firewall.FirewallCapabilities;
import com.ghostchu.peerbanhelper.platform.firewall.FirewallCrashCleanup;
import com.ghostchu.peerbanhelper.platform.firewall.FirewallException;
import com.ghostchu.peerbanhelper.platform.firewall.UnsupportedFirewall;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Process-scoped nftables firewall backed by libnftables through FFM.
 */
@Slf4j
public final class LinuxFirewall implements Firewall {
    private static final long CAP_NET_ADMIN = 1L << 12;
    private static final Pattern TABLE_LINE = Pattern.compile(
            "(?m)^table\\s+inet\\s+(" + Pattern.quote(NftablesNamespace.PREFIX) + "\\d+_[0-9a-f]{12})$"
    );

    private final ReentrantLock lock = new ReentrantLock();
    private final String tableName;
    private final NftablesNative nativeApi;
    private final FirewallCapabilities capabilities;
    private LinkedHashSet<InetAddress> managedAddresses = new LinkedHashSet<>();
    private boolean closed;

    private LinuxFirewall() {
        this.tableName = NftablesNamespace.create(ProcessHandle.current().pid());
        this.nativeApi = new NftablesNative();
        try {
            removeDeadNamespaces();
            createNamespace();
        } catch (FirewallException exception) {
            try {
                nativeApi.close();
            } catch (FirewallException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
        this.capabilities = new FirewallCapabilities(
                FirewallBackend.NFTABLES,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                true,
                FirewallCrashCleanup.NEXT_START_SWEEP,
                "nftables system-wide filtering; orphan cleanup runs on the next start"
        );
    }

    public static Firewall create() {
        String privilegeFailure = checkNetAdminCapability();
        if (privilegeFailure != null) {
            return unavailable(privilegeFailure);
        }
        try {
            return new LinuxFirewall();
        } catch (Throwable throwable) {
            String detail = "nftables backend unavailable: " + throwable.getMessage();
            log.debug("Unable to initialize nftables firewall backend", throwable);
            return unavailable(detail);
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
            return FirewallAddresses.copyOf(managedAddresses);
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
            added.removeAll(managedAddresses);
            if (added.isEmpty()) {
                return;
            }
            nativeApi.run(canAddCommands(added));
            managedAddresses.addAll(added);
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
            removed.retainAll(managedAddresses);
            if (removed.isEmpty()) {
                return;
            }
            nativeApi.run(canRemoveCommands(removed));
            managedAddresses.removeAll(removed);
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
            String command = flushCommands();
            if (!requested.isEmpty()) {
                command += "\n" + canAddCommands(requested);
            }
            nativeApi.run(command);
            managedAddresses = new LinkedHashSet<>(requested);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            requireOpen();
            if (managedAddresses.isEmpty()) {
                return;
            }
            nativeApi.run(flushCommands());
            managedAddresses.clear();
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
            managedAddresses = new LinkedHashSet<>();
            FirewallException failure = null;
            try {
                nativeApi.run("delete table inet " + tableName);
            } catch (FirewallException exception) {
                failure = exception;
            } finally {
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
                throw new FirewallException("Unable to remove the nftables firewall namespace", failure);
            }
        } finally {
            lock.unlock();
        }
    }

    private void createNamespace() {
        nativeApi.run("""
                add table inet %1$s
                add set inet %1$s %2$s { type ipv4_addr; }
                add set inet %1$s %3$s { type ipv6_addr; }
                add chain inet %1$s input { type filter hook input priority 0; policy accept; }
                add chain inet %1$s output { type filter hook output priority 0; policy accept; }
                add rule inet %1$s input ip saddr @%2$s drop
                add rule inet %1$s input ip6 saddr @%3$s drop
                add rule inet %1$s output ip daddr @%2$s drop
                add rule inet %1$s output ip6 daddr @%3$s drop
                """.formatted(tableName, ipv4Set(), ipv6Set()));
    }

    private void removeDeadNamespaces() {
        String tables = nativeApi.run("list tables");
        Matcher matcher = TABLE_LINE.matcher(tables);
        while (matcher.find()) {
            String staleTable = matcher.group(1);
            OptionalLong owner = NftablesNamespace.ownerPid(staleTable);
            if (owner.isEmpty() || isProcessAlive(owner.getAsLong())) {
                continue;
            }
            try {
                nativeApi.run("delete table inet " + staleTable);
                log.info("Removed stale PeerBanHelper nftables table: {}", staleTable);
            } catch (FirewallException exception) {
                log.warn("Unable to remove stale PeerBanHelper nftables table {}", staleTable, exception);
            }
        }
    }

    private String canAddCommands(Collection<InetAddress> addresses) {
        StringBuilder command = new StringBuilder();
        for (InetAddress address : addresses) {
            if (command.length() > 0) {
                command.append('\n');
            }
            command.append("add element inet ")
                    .append(tableName)
                    .append(' ')
                    .append(setFor(address))
                    .append(" { ")
                    .append(formatAddress(address))
                    .append(" }");
        }
        return command.toString();
    }

    private String canRemoveCommands(Collection<InetAddress> addresses) {
        StringBuilder command = new StringBuilder();
        for (InetAddress address : addresses) {
            if (command.length() > 0) {
                command.append('\n');
            }
            command.append("delete element inet ")
                    .append(tableName)
                    .append(' ')
                    .append(setFor(address))
                    .append(" { ")
                    .append(formatAddress(address))
                    .append(" }");
        }
        return command.toString();
    }

    private String flushCommands() {
        return "flush set inet " + tableName + " " + ipv4Set()
                + "\nflush set inet " + tableName + " " + ipv6Set();
    }

    private String setFor(InetAddress address) {
        if (address instanceof Inet4Address) {
            return ipv4Set();
        }
        if (address instanceof Inet6Address) {
            return ipv6Set();
        }
        throw new IllegalArgumentException("Unsupported address type: " + address);
    }

    private String formatAddress(InetAddress address) {
        if (address instanceof Inet6Address inet6Address
                && (inet6Address.getScopedInterface() != null || inet6Address.getScopeId() != 0)) {
            throw new IllegalArgumentException("Scoped IPv6 addresses are not supported: " + address);
        }
        return address.getHostAddress();
    }

    private String ipv4Set() {
        return "peerbanhelper_ipv4";
    }

    private String ipv6Set() {
        return "peerbanhelper_ipv6";
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Firewall is closed");
        }
    }

    private static boolean isProcessAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    private static Firewall unavailable(String detail) {
        return new UnsupportedFirewall(new FirewallCapabilities(
                FirewallBackend.NFTABLES,
                false,
                true,
                true,
                true,
                true,
                true,
                false,
                true,
                FirewallCrashCleanup.NEXT_START_SWEEP,
                detail
        ));
    }

    private static String checkNetAdminCapability() {
        Path status = Path.of("/proc/self/status");
        if (!Files.isReadable(status)) {
            return "nftables backend requires Linux /proc capability information";
        }
        try {
            for (String line : Files.readAllLines(status)) {
                if (!line.startsWith("CapEff:")) {
                    continue;
                }
                long effective = Long.parseUnsignedLong(line.substring("CapEff:".length()).trim(), 16);
                if ((effective & CAP_NET_ADMIN) == 0) {
                    return "nftables backend requires CAP_NET_ADMIN (normally root or container capability)";
                }
                return null;
            }
            return "nftables backend could not determine effective Linux capabilities";
        } catch (IOException | NumberFormatException exception) {
            return "nftables backend could not validate CAP_NET_ADMIN: " + exception.getMessage();
        }
    }
}
