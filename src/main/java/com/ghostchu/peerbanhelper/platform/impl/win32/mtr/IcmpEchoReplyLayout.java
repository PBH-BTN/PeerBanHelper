package com.ghostchu.peerbanhelper.platform.impl.win32.mtr;

import java.lang.foreign.*;

/**
 * Memory layout helpers for Windows ICMP reply structures.
 *
 * <h2>IP_OPTION_INFORMATION (used as RequestOptions)</h2>
 * <pre>
 * typedef struct ip_option_information {
 *   UCHAR  Ttl;          // 1 byte  offset 0
 *   UCHAR  Tos;          // 1 byte  offset 1
 *   UCHAR  Flags;        // 1 byte  offset 2
 *   UCHAR  OptionsSize;  // 1 byte  offset 3
 *   PUCHAR OptionsData;  // pointer offset 4  (8 bytes on 64-bit)
 * } IP_OPTION_INFORMATION;
 * </pre>
 * Total size: 12 bytes on 64-bit Windows (4 bytes + 4-byte padding + 8-byte ptr).
 *
 * <h2>ICMP_ECHO_REPLY (IPv4)</h2>
 * <pre>
 * typedef struct icmp_echo_reply {
 *   IPAddr  Address;      // DWORD  offset 0
 *   ULONG   Status;       // DWORD  offset 4
 *   ULONG   RoundTripTime;// DWORD  offset 8
 *   USHORT  DataSize;     // WORD   offset 12
 *   USHORT  Reserved;     // WORD   offset 14
 *   PVOID   Data;         // PTR    offset 16 (8 bytes on 64-bit)
 *   IP_OPTION_INFORMATION Options; // offset 24
 * } ICMP_ECHO_REPLY;
 * </pre>
 *
 * <h2>ICMPV6_ECHO_REPLY (IPv6)</h2>
 * <pre>
 * typedef struct icmpv6_echo_reply_lh {
 *   IPV6_ADDRESS_EX Address;   // 28 bytes  offset 0
 *   ULONG           Status;    // DWORD     offset 28
 *   unsigned int    RoundTripTime; // DWORD offset 32
 * } ICMPV6_ECHO_REPLY;
 * </pre>
 * IPV6_ADDRESS_EX:
 *   USHORT sin6_port   (2)
 *   ULONG  sin6_flowinfo (4)
 *   USHORT sin6_addr[8] (16)
 *   ULONG  sin6_scope_id (4)
 *   = 26 bytes, padded to 28 with 2 bytes
 */
public final class IcmpEchoReplyLayout {

    // =========================================================================
    // IP_OPTION_INFORMATION
    // =========================================================================

    /**
     * Layout of {@code IP_OPTION_INFORMATION} on 64-bit Windows.
     *
     * <pre>
     * UCHAR  Ttl;         offset 0
     * UCHAR  Tos;         offset 1
     * UCHAR  Flags;       offset 2
     * UCHAR  OptionsSize; offset 3
     * PUCHAR OptionsData; offset 8  (4-byte alignment padding at offset 4–7)
     * </pre>
     */
    public static final StructLayout IP_OPTION_INFORMATION = MemoryLayout.structLayout(
            ValueLayout.JAVA_BYTE.withName("Ttl"),           // offset 0
            ValueLayout.JAVA_BYTE.withName("Tos"),           // offset 1
            ValueLayout.JAVA_BYTE.withName("Flags"),         // offset 2
            ValueLayout.JAVA_BYTE.withName("OptionsSize"),   // offset 3
            MemoryLayout.paddingLayout(4),                   // padding to align pointer
            ValueLayout.ADDRESS.withName("OptionsData")      // offset 8
    );

    public static final long IP_OPT_OFFSET_TTL = IP_OPTION_INFORMATION
            .byteOffset(MemoryLayout.PathElement.groupElement("Ttl"));
    public static final long IP_OPT_OFFSET_TOS = IP_OPTION_INFORMATION
            .byteOffset(MemoryLayout.PathElement.groupElement("Tos"));
    public static final long IP_OPT_OFFSET_FLAGS = IP_OPTION_INFORMATION
            .byteOffset(MemoryLayout.PathElement.groupElement("Flags"));

    // =========================================================================
    // ICMP_ECHO_REPLY (IPv4)
    // =========================================================================

    /**
     * Layout of {@code ICMP_ECHO_REPLY} on 64-bit Windows.
     * We only need the first three DWORD fields for MTR; the rest is ignored.
     */
    public static final StructLayout ICMP_ECHO_REPLY = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("Address"),        // offset 0  – reply source IPv4 (big-endian)
            ValueLayout.JAVA_INT.withName("Status"),         // offset 4  – IcmpLib.IP_* constants
            ValueLayout.JAVA_INT.withName("RoundTripTime"),  // offset 8  – RTT in milliseconds
            ValueLayout.JAVA_SHORT.withName("DataSize"),     // offset 12
            ValueLayout.JAVA_SHORT.withName("Reserved"),     // offset 14
            ValueLayout.ADDRESS.withName("Data"),            // offset 16 – pointer
            // Embed IP_OPTION_INFORMATION (16 bytes)
            ValueLayout.JAVA_BYTE.withName("opt_Ttl"),
            ValueLayout.JAVA_BYTE.withName("opt_Tos"),
            ValueLayout.JAVA_BYTE.withName("opt_Flags"),
            ValueLayout.JAVA_BYTE.withName("opt_OptionsSize"),
            MemoryLayout.paddingLayout(4),
            ValueLayout.ADDRESS.withName("opt_OptionsData")
    );

    public static final long ECHO_REPLY_OFFSET_ADDRESS =
            ICMP_ECHO_REPLY.byteOffset(MemoryLayout.PathElement.groupElement("Address"));
    public static final long ECHO_REPLY_OFFSET_STATUS =
            ICMP_ECHO_REPLY.byteOffset(MemoryLayout.PathElement.groupElement("Status"));
    public static final long ECHO_REPLY_OFFSET_RTT =
            ICMP_ECHO_REPLY.byteOffset(MemoryLayout.PathElement.groupElement("RoundTripTime"));

    // =========================================================================
    // ICMPV6_ECHO_REPLY (IPv6)
    // =========================================================================

    /**
     * Layout for {@code ICMPV6_ECHO_REPLY} on 64-bit Windows.
     *
     * <pre>
     * USHORT  sin6_port       offset 0   (2 bytes)
     * ULONG   sin6_flowinfo   offset 2   (4 bytes – unaligned in C struct)
     * USHORT  sin6_addr[8]    offset 6   (16 bytes)
     * ULONG   sin6_scope_id   offset 22  (4 bytes)
     *                         2 bytes padding → total Address = 28 bytes
     * ULONG   Status          offset 28
     * UINT    RoundTripTime   offset 32
     * </pre>
     */
    // We model this as a flat byte array for the address part plus two ints.
    // The address bytes [6..21] are the 16-byte IPv6 address.
    public static final long ECHO6_REPLY_SIZE = 36L; // 28 + 4 + 4

    /** Byte offset of the 16-byte IPv6 address inside ICMPV6_ECHO_REPLY. */
    public static final int  ECHO6_ADDR_OFFSET    = 6;
    /** Byte offset of Status (ULONG) inside ICMPV6_ECHO_REPLY. */
    public static final int  ECHO6_STATUS_OFFSET  = 28;
    /** Byte offset of RoundTripTime (UINT) inside ICMPV6_ECHO_REPLY. */
    public static final int  ECHO6_RTT_OFFSET     = 32;

    // =========================================================================
    // sockaddr_in6 layout (for Icmp6SendEcho2 src/dst parameters)
    // =========================================================================

    /**
     * Minimal {@code sockaddr_in6} layout (28 bytes on Windows):
     * <pre>
     * USHORT  sin6_family    offset 0
     * USHORT  sin6_port      offset 2
     * ULONG   sin6_flowinfo  offset 4
     * UCHAR   sin6_addr[16]  offset 8
     * ULONG   sin6_scope_id  offset 24
     * </pre>
     */
    public static final long SOCKADDR_IN6_SIZE = 28L;

    public static final int SA6_OFFSET_FAMILY    = 0;
    public static final int SA6_OFFSET_PORT      = 2;
    public static final int SA6_OFFSET_FLOWINFO  = 4;
    public static final int SA6_OFFSET_ADDR      = 8;
    public static final int SA6_OFFSET_SCOPE_ID  = 24;

    /** AF_INET6 on Windows. */
    public static final short AF_INET6 = 23;

    // Minimum reply buffer sizes
    /** Minimum reply buffer for one IPv4 echo reply. */
    public static final int  IPV4_REPLY_BUFFER_SIZE = 1024;
    /** Minimum reply buffer for one IPv6 echo reply. */
    public static final int  IPV6_REPLY_BUFFER_SIZE = 1024;

    private IcmpEchoReplyLayout() {}
}

