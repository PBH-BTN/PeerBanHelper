package com.ghostchu.peerbanhelper.platform.impl.win32.firewall;

import com.ghostchu.peerbanhelper.platform.firewall.Firewall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.InetAddress;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfEnvironmentVariable(named = "PBH_WFP_INTEGRATION", matches = "true")
class WindowsFirewallIntegrationTest {
    @Test
    void managesTestNetAddressesAndClosesCleanly() throws Exception {
        Firewall firewall = WindowsFirewall.create();
        assumeTrue(firewall.capabilities().available(), firewall.capabilities().detail());
        InetAddress ipv4 = InetAddress.ofLiteral("192.0.2.60");
        InetAddress ipv6 = InetAddress.ofLiteral("2001:db8::60");

        try (firewall) {
            firewall.replace(Set.of(ipv4, ipv6));
            assertEquals(Set.of(ipv4, ipv6), firewall.addresses());
            firewall.remove(Set.of(ipv4));
            assertEquals(Set.of(ipv6), firewall.addresses());
            firewall.clear();
            assertTrue(firewall.addresses().isEmpty());
        }
    }
}
