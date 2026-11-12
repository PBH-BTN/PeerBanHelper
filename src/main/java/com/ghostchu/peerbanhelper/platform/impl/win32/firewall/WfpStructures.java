package com.ghostchu.peerbanhelper.platform.impl.win32.firewall;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static java.lang.foreign.MemoryLayout.paddingLayout;
import static java.lang.foreign.MemoryLayout.sequenceLayout;
import static java.lang.foreign.MemoryLayout.structLayout;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * Native WFP structures used by the management API.
 */
final class WfpStructures {
    static final int ERROR_SUCCESS = 0;
    static final int RPC_C_AUTHN_WINNT = 10;
    static final int FWPM_SESSION_FLAG_DYNAMIC = 1;
    static final int FWP_EMPTY = 0;
    static final int FWP_V4_ADDR_MASK = 0x100;
    static final int FWP_BYTE_ARRAY16_TYPE = 11;
    static final int FWP_MATCH_EQUAL = 0;
    static final int FWP_ACTION_BLOCK = 0x1001;
    static final int FWP_E_FILTER_NOT_FOUND = 0x80320003;

    static final UUID LAYER_ALE_AUTH_CONNECT_V4 =
            UUID.fromString("c38d57d1-05a7-4c33-904f-7fbceee60e82");
    static final UUID LAYER_ALE_AUTH_CONNECT_V6 =
            UUID.fromString("4a72393b-319f-44bc-84c3-ba54dcb3b6b4");
    static final UUID LAYER_ALE_AUTH_RECV_ACCEPT_V4 =
            UUID.fromString("e1cd9fe7-f4b5-4273-96c0-592e487b8650");
    static final UUID LAYER_ALE_AUTH_RECV_ACCEPT_V6 =
            UUID.fromString("a3b42c97-9f04-4672-b87e-cee9c483257f");
    static final UUID CONDITION_IP_REMOTE_ADDRESS =
            UUID.fromString("b235ae9a-1d64-49b8-a44c-5ff3d9095045");

    static final MemoryLayout GUID = structLayout(
            JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN).withName("data1"),
            JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN).withName("data2"),
            JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN).withName("data3"),
            sequenceLayout(8, JAVA_BYTE).withName("data4")
    ).withName("GUID");

    static final MemoryLayout DISPLAY_DATA = structLayout(
            ADDRESS.withName("name"),
            ADDRESS.withName("description")
    ).withName("FWPM_DISPLAY_DATA0");

    static final MemoryLayout BYTE_BLOB = structLayout(
            JAVA_INT.withName("size"),
            paddingLayout(4),
            ADDRESS.withName("data")
    ).withName("FWP_BYTE_BLOB");

    static final MemoryLayout VALUE = structLayout(
            JAVA_INT.withName("type"),
            paddingLayout(4),
            ADDRESS.withName("value")
    ).withName("FWP_VALUE0");

    static final MemoryLayout ACTION = structLayout(
            JAVA_INT.withName("type"),
            GUID.withName("actionKey")
    ).withName("FWPM_ACTION0");

    static final MemoryLayout CONDITION_VALUE0 = structLayout(
            JAVA_INT.withName("type"),
            paddingLayout(4),
            ADDRESS.withName("value")
    ).withName("FWP_CONDITION_VALUE0");

    static final MemoryLayout FILTER_CONDITION = structLayout(
            GUID.withName("fieldKey"),
            JAVA_INT.withName("matchType"),
            paddingLayout(4),
            CONDITION_VALUE0.withName("conditionValue")
    ).withName("FWPM_FILTER_CONDITION0");

    static final MemoryLayout SESSION = structLayout(
            GUID.withName("sessionKey"),
            DISPLAY_DATA.withName("displayData"),
            JAVA_INT.withName("flags"),
            JAVA_INT.withName("txnWaitTimeoutInMSec"),
            JAVA_INT.withName("processId"),
            paddingLayout(4),
            ADDRESS.withName("sid"),
            ADDRESS.withName("username"),
            JAVA_INT.withName("kernelMode"),
            paddingLayout(4)
    ).withName("FWPM_SESSION0");

    static final MemoryLayout PROVIDER = structLayout(
            GUID.withName("providerKey"),
            DISPLAY_DATA.withName("displayData"),
            JAVA_INT.withName("flags"),
            paddingLayout(4),
            BYTE_BLOB.withName("providerData"),
            ADDRESS.withName("serviceName")
    ).withName("FWPM_PROVIDER0");

    static final MemoryLayout SUBLAYER = structLayout(
            GUID.withName("subLayerKey"),
            DISPLAY_DATA.withName("displayData"),
            JAVA_INT.withName("flags"),
            paddingLayout(4),
            ADDRESS.withName("providerKey"),
            BYTE_BLOB.withName("providerData"),
            JAVA_SHORT.withName("weight"),
            paddingLayout(6)
    ).withName("FWPM_SUBLAYER0");

    static final MemoryLayout FILTER = structLayout(
            GUID.withName("filterKey"),
            DISPLAY_DATA.withName("displayData"),
            JAVA_INT.withName("flags"),
            paddingLayout(4),
            ADDRESS.withName("providerKey"),
            BYTE_BLOB.withName("providerData"),
            GUID.withName("layerKey"),
            GUID.withName("subLayerKey"),
            VALUE.withName("weight"),
            JAVA_INT.withName("numFilterConditions"),
            paddingLayout(4),
            ADDRESS.withName("filterCondition"),
            ACTION.withName("action"),
            paddingLayout(4),
            JAVA_LONG.withName("rawContext"),
            ADDRESS.withName("reserved"),
            JAVA_LONG.withName("filterId"),
            VALUE.withName("effectiveWeight")
    ).withName("FWPM_FILTER0");

    static final long SESSION_NAME = 16;
    static final long SESSION_FLAGS = 32;
    static final long SESSION_TXN_WAIT = 36;

    static final long PROVIDER_DISPLAY_NAME = 16;
    static final long PROVIDER_SERVICE_NAME = 56;

    static final long SUBLAYER_DISPLAY_NAME = 16;
    static final long SUBLAYER_PROVIDER_KEY = 40;
    static final long SUBLAYER_WEIGHT = 64;

    static final long FILTER_DISPLAY_NAME = 16;
    static final long FILTER_PROVIDER_KEY = 40;
    static final long FILTER_LAYER_KEY = 64;
    static final long FILTER_SUBLAYER_KEY = 80;
    static final long FILTER_WEIGHT = 96;
    static final long FILTER_NUM_CONDITIONS = 112;
    static final long FILTER_CONDITIONS = 120;
    static final long FILTER_ACTION = 128;

    static final long CONDITION_FIELD_KEY = 0;
    static final long CONDITION_MATCH_TYPE = 16;
    static final long CONDITION_VALUE_OFFSET = 24;
    static final long CONDITION_VALUE_TYPE = 24;
    static final long CONDITION_VALUE_DATA = 32;

    static {
        requireSize("GUID", GUID, 16);
        requireSize("FWPM_SESSION0", SESSION, 72);
        requireSize("FWPM_PROVIDER0", PROVIDER, 64);
        requireSize("FWPM_SUBLAYER0", SUBLAYER, 72);
        requireSize("FWPM_FILTER_CONDITION0", FILTER_CONDITION, 40);
        requireSize("FWPM_FILTER0", FILTER, 192);
    }

    private WfpStructures() {
    }

    static void writeGuid(MemorySegment target, long offset, UUID uuid) {
        ByteBuffer bytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        bytes.putInt((int) (most >>> 32));
        bytes.putShort((short) (most >>> 16));
        bytes.putShort((short) most);
        bytes.put(ByteBuffer.allocate(Long.BYTES).putLong(least).array());
        MemorySegment.copy(MemorySegment.ofArray(bytes.array()), 0, target, offset, 16);
    }

    static MemorySegment wideString(Arena arena, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_16LE);
        MemorySegment segment = arena.allocate(encoded.length + 2L, 2);
        MemorySegment.copy(MemorySegment.ofArray(encoded), 0, segment, 0, encoded.length);
        segment.set(JAVA_BYTE, encoded.length, (byte) 0);
        segment.set(JAVA_BYTE, encoded.length + 1L, (byte) 0);
        return segment;
    }

    static void writeAddressCondition(Arena arena, MemorySegment condition, InetAddress address) {
        writeGuid(condition, CONDITION_FIELD_KEY, CONDITION_IP_REMOTE_ADDRESS);
        condition.set(JAVA_INT, CONDITION_MATCH_TYPE, FWP_MATCH_EQUAL);
        if (address instanceof Inet4Address) {
            MemorySegment addressAndMask = arena.allocate(8, 4);
            byte[] raw = address.getAddress();
            MemorySegment.copy(MemorySegment.ofArray(raw), 0, addressAndMask, 0, raw.length);
            for (long offset = 4; offset < 8; offset++) {
                addressAndMask.set(JAVA_BYTE, offset, (byte) 0xFF);
            }
            condition.set(JAVA_INT, CONDITION_VALUE_TYPE, FWP_V4_ADDR_MASK);
            condition.set(ADDRESS, CONDITION_VALUE_DATA, addressAndMask);
            return;
        }
        if (address instanceof Inet6Address) {
            MemorySegment addressBytes = arena.allocate(16, 1);
            byte[] raw = address.getAddress();
            MemorySegment.copy(MemorySegment.ofArray(raw), 0, addressBytes, 0, raw.length);
            condition.set(JAVA_INT, CONDITION_VALUE_TYPE, FWP_BYTE_ARRAY16_TYPE);
            condition.set(ADDRESS, CONDITION_VALUE_DATA, addressBytes);
            return;
        }
        throw new IllegalArgumentException("Unsupported address type: " + address);
    }

    private static void requireSize(String name, MemoryLayout layout, long expected) {
        if (layout.byteSize() != expected) {
            throw new ExceptionInInitializerError(
                    name + " layout size is " + layout.byteSize() + ", expected " + expected
            );
        }
    }
}
