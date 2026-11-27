package com.ghostchu.peerbanhelper.platform.impl.linux.firewall;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NftablesNamespaceTest {
    @Test
    void parsesOwnedTablePid() {
        String name = NftablesNamespace.create(4242);
        assertTrue(name.startsWith(NftablesNamespace.PREFIX + "4242_"));
        assertEquals(4242, NftablesNamespace.ownerPid(name).orElseThrow());
    }

    @Test
    void rejectsForeignAndMalformedNames() {
        assertTrue(NftablesNamespace.ownerPid("filter").isEmpty());
        assertTrue(NftablesNamespace.ownerPid("peerbanhelper_firewall_x_001122334455").isEmpty());
        assertTrue(NftablesNamespace.ownerPid("peerbanhelper_firewall_42_short").isEmpty());
    }
}
