package com.ghostchu.peerbanhelper.platform.impl.win32.firewall;

import com.ghostchu.peerbanhelper.platform.firewall.Firewall;
import com.ghostchu.peerbanhelper.platform.firewall.FirewallBackend;
import com.ghostchu.peerbanhelper.platform.firewall.FirewallCrashCleanup;
import com.ghostchu.peerbanhelper.platform.firewall.FirewallUnavailableException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class WindowsFirewallTest {
    @Test
    void nonElevatedProcessReportsUnavailableWithoutCreatingFilters() throws Exception {
        assumeFalse(WindowsPrivileges.isElevated(), "integration test requires a non-elevated process");
        Firewall firewall = WindowsFirewall.create();
        var capabilities = firewall.capabilities();

        assertFalse(capabilities.available());
        assertTrue(capabilities.ipv4());
        assertTrue(capabilities.ipv6());
        assertTrue(capabilities.inbound());
        assertTrue(capabilities.outbound());
        assertTrue(capabilities.systemWide());
        assertFalse(capabilities.processFiltering());
        assertTrue(capabilities.requiresElevation());
        assertEquals(FirewallBackend.WINDOWS_FILTERING_PLATFORM, capabilities.backend());
        assertEquals(FirewallCrashCleanup.DYNAMIC_SESSION, capabilities.crashCleanup());
        assertThrows(
                FirewallUnavailableException.class,
                () -> firewall.add(List.of(InetAddress.ofLiteral("192.0.2.1")))
        );
    }
}
