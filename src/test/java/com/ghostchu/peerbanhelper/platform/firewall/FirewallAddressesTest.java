package com.ghostchu.peerbanhelper.platform.firewall;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.Inet6Address;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FirewallAddressesTest {
    @Test
    void normalizeDeduplicatesAndPreservesOrder() throws Exception {
        InetAddress ipv4 = InetAddress.ofLiteral("192.0.2.10");
        InetAddress ipv6 = InetAddress.ofLiteral("2001:db8::10");

        var normalized = FirewallAddresses.normalize(List.of(ipv4, ipv6, ipv4));

        assertEquals(List.of(ipv4, ipv6), List.copyOf(normalized));
        assertThrows(UnsupportedOperationException.class, () -> normalized.add(ipv4));
    }

    @Test
    void copyOfRejectsMutation() throws Exception {
        InetAddress address = InetAddress.ofLiteral("192.0.2.20");
        var snapshot = FirewallAddresses.copyOf(List.of(address));

        assertEquals(List.of(address), List.copyOf(snapshot));
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    void scopedIpv6IsRejected() throws Exception {
        Inet6Address scoped = Inet6Address.getByAddress(null, new byte[16], 1);
        assertThrows(IllegalArgumentException.class,
                () -> FirewallAddresses.normalize(List.of(scoped)));
    }
}
