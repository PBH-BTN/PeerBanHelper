package com.ghostchu.peerbanhelper.platform.firewall;

import org.jetbrains.annotations.NotNull;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Address validation and immutable snapshot helpers shared by firewall backends.
 */
public final class FirewallAddresses {
    private FirewallAddresses() {
    }

    @NotNull
    public static Set<InetAddress> normalize(@NotNull Collection<? extends InetAddress> addresses) {
        LinkedHashSet<InetAddress> normalized = new LinkedHashSet<>(addresses.size());
        for (InetAddress address : addresses) {
            if (!(address instanceof Inet4Address) && !(address instanceof Inet6Address)) {
                throw new IllegalArgumentException("Unsupported address type: " + address);
            }
            if (address instanceof Inet6Address inet6Address
                    && (inet6Address.getScopedInterface() != null || inet6Address.getScopeId() != 0)) {
                throw new IllegalArgumentException("Scoped IPv6 addresses are not supported: " + address);
            }
            normalized.add(address);
        }
        return Collections.unmodifiableSet(normalized);
    }

    @NotNull
    public static Set<InetAddress> copyOf(@NotNull Collection<? extends InetAddress> addresses) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(addresses));
    }
}
