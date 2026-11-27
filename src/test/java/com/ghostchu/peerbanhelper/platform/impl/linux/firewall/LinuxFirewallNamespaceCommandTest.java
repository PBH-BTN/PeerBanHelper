package com.ghostchu.peerbanhelper.platform.impl.linux.firewall;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinuxFirewallNamespaceCommandTest {
    @Test
    void tableIsOwnedByTheProcessAndNotMarkedPersistent() {
        String command = LinuxFirewall.createNamespaceCommand(
                "peerbanhelper_firewall_42_001122334455",
                "peerbanhelper_ipv4",
                "peerbanhelper_ipv6"
        );

        assertTrue(command.contains("add table inet peerbanhelper_firewall_42_001122334455 { flags owner; }"));
        assertFalse(command.contains("persist"));
    }
}
