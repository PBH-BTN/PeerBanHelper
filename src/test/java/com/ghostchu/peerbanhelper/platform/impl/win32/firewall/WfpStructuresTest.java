package com.ghostchu.peerbanhelper.platform.impl.win32.firewall;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.InetAddress;
import java.util.UUID;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WfpStructuresTest {
    @Test
    void nativeLayoutsMatchTheWindowsAbi() {
        assertEquals(16, WfpStructures.GUID.byteSize());
        assertEquals(72, WfpStructures.SESSION.byteSize());
        assertEquals(64, WfpStructures.PROVIDER.byteSize());
        assertEquals(72, WfpStructures.SUBLAYER.byteSize());
        assertEquals(40, WfpStructures.FILTER_CONDITION.byteSize());
        assertEquals(192, WfpStructures.FILTER.byteSize());
    }

    @Test
    void guidUsesWindowsLittleEndianPrefix() {
        UUID uuid = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment guid = arena.allocate(WfpStructures.GUID);
            WfpStructures.writeGuid(guid, 0, uuid);
            assertArrayEquals(
                    new byte[]{
                            0x33, 0x22, 0x11, 0x00,
                            0x55, 0x44,
                            0x77, 0x66,
                            (byte) 0x88, (byte) 0x99,
                            (byte) 0xaa, (byte) 0xbb,
                            (byte) 0xcc, (byte) 0xdd,
                            (byte) 0xee, (byte) 0xff
                    },
                    guid.toArray(JAVA_BYTE)
            );
        }
    }

    @Test
    void ipv4ConditionUsesAnExactAddressMask() throws Exception {
        byte[] addressBytes = new byte[]{10, 20, 30, 40};
        InetAddress address = InetAddress.getByAddress(addressBytes);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment condition = arena.allocate(WfpStructures.FILTER_CONDITION);
            WfpStructures.writeAddressCondition(arena, condition, address);

            assertEquals(WfpStructures.FWP_V4_ADDR_MASK,
                    condition.get(JAVA_INT, WfpStructures.CONDITION_VALUE_TYPE));
            MemorySegment addressAndMask = condition
                    .get(ADDRESS, WfpStructures.CONDITION_VALUE_DATA)
                    .reinterpret(8);
            assertArrayEquals(addressBytes, addressAndMask.asSlice(0, 4).toArray(JAVA_BYTE));
            assertArrayEquals(new byte[]{-1, -1, -1, -1},
                    addressAndMask.asSlice(4, 4).toArray(JAVA_BYTE));
        }
    }

    @Test
    void ipv6ConditionUsesSixteenAddressBytes() throws Exception {
        byte[] addressBytes = new byte[16];
        for (int i = 0; i < addressBytes.length; i++) {
            addressBytes[i] = (byte) i;
        }
        InetAddress address = InetAddress.getByAddress(addressBytes);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment condition = arena.allocate(WfpStructures.FILTER_CONDITION);
            WfpStructures.writeAddressCondition(arena, condition, address);

            assertEquals(WfpStructures.FWP_BYTE_ARRAY16_TYPE,
                    condition.get(JAVA_INT, WfpStructures.CONDITION_VALUE_TYPE));
            MemorySegment addressSegment = condition
                    .get(ADDRESS, WfpStructures.CONDITION_VALUE_DATA)
                    .reinterpret(16);
            assertArrayEquals(addressBytes, addressSegment.toArray(JAVA_BYTE));
        }
    }
}
