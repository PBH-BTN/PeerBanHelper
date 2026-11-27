package com.ghostchu.peerbanhelper.platform.firewall;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnsupportedFirewallTest {
    @Test
    void reportsReasonAndRejectsMutations() throws Exception {
        UnsupportedFirewall firewall = new UnsupportedFirewall("not available in this test");

        assertFalse(firewall.capabilities().available());
        assertTrue(firewall.addresses().isEmpty());
        assertThrows(
                FirewallUnavailableException.class,
                () -> firewall.add(List.of(InetAddress.ofLiteral("192.0.2.30")))
        );
        firewall.close();
        firewall.close();
    }

    @Test
    void rejectsAnAvailableCapabilitySnapshot() {
        FirewallCapabilities capabilities = new FirewallCapabilities(
                FirewallBackend.UNSUPPORTED,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                FirewallCrashCleanup.NONE,
                "invalid"
        );
        assertThrows(IllegalArgumentException.class, () -> new UnsupportedFirewall(capabilities));
    }
}
