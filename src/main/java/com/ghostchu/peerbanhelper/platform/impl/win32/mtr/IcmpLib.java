package com.ghostchu.peerbanhelper.platform.impl.win32.mtr;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * FFM bindings for the Windows ICMP helper API provided by {@code iphlpapi.dll}.
 *
 * <p>Windows exposes a high-level ICMP API that does NOT require administrator
 * privileges, which makes it ideal for MTR:
 * <ul>
 *   <li>{@code IcmpCreateFile} / {@code Icmp6CreateFile} – open an ICMP handle</li>
 *   <li>{@code IcmpSendEcho2Ex} / {@code Icmp6SendEcho2} – send one echo request
 *       with a specific TTL and wait for the reply (supports timeout)</li>
 *   <li>{@code IcmpCloseHandle} – release the handle</li>
 * </ul>
 *
 * <p>All method handles are initialised once at class-load time using
 * {@link Arena#global()} so they survive for the lifetime of the JVM.
 */
public final class IcmpLib {

    // -------------------------------------------------------------------------
    // Library / Linker setup
    // -------------------------------------------------------------------------

    private static final Linker          LINKER    = Linker.nativeLinker();
    private static final SymbolLookup    IPHLPAPI  = SymbolLookup.libraryLookup("iphlpapi", Arena.global());

    // -------------------------------------------------------------------------
    // Constant: invalid handle value returned on failure
    // -------------------------------------------------------------------------

    /** Equivalent to {@code INVALID_HANDLE_VALUE} (cast to pointer) on 64-bit Windows. */
    public static final long INVALID_HANDLE_VALUE = -1L;  // 0xFFFFFFFF_FFFFFFFF

    // -------------------------------------------------------------------------
    // Return codes embedded in ICMP_ECHO_REPLY.Status
    // -------------------------------------------------------------------------

    /** IP_SUCCESS – the destination echoed our packet. */
    public static final int IP_SUCCESS               = 0;
    /** IP_TTL_EXPIRED_TRANSIT – intermediate router replied "TTL expired". */
    public static final int IP_TTL_EXPIRED_TRANSIT   = 11013;
    /** IP_TTL_EXPIRED_REASSEM – TTL expired during reassembly (rare). */
    public static final int IP_TTL_EXPIRED_REASSEM   = 11014;
    /** IP_REQ_TIMED_OUT – probe timed out. */
    public static final int IP_REQ_TIMED_OUT         = 11010;
    /** IP_DEST_NET_UNREACHABLE */
    public static final int IP_DEST_NET_UNREACHABLE  = 11002;
    /** IP_DEST_HOST_UNREACHABLE */
    public static final int IP_DEST_HOST_UNREACHABLE = 11003;
    /** IP_DEST_PORT_UNREACHABLE */
    public static final int IP_DEST_PORT_UNREACHABLE = 11005;

    // -------------------------------------------------------------------------
    // HANDLE IcmpCreateFile()
    // -------------------------------------------------------------------------

    private static final MethodHandle MH_ICMP_CREATE_FILE = LINKER.downcallHandle(
            IPHLPAPI.find("IcmpCreateFile").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS)
    );

    // -------------------------------------------------------------------------
    // HANDLE Icmp6CreateFile()
    // -------------------------------------------------------------------------

    private static final MethodHandle MH_ICMP6_CREATE_FILE = LINKER.downcallHandle(
            IPHLPAPI.find("Icmp6CreateFile").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS)
    );

    // -------------------------------------------------------------------------
    // BOOL IcmpCloseHandle(HANDLE IcmpHandle)
    // -------------------------------------------------------------------------

    private static final MethodHandle MH_ICMP_CLOSE_HANDLE = LINKER.downcallHandle(
            IPHLPAPI.find("IcmpCloseHandle").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS)   // IcmpHandle
    );

    // -------------------------------------------------------------------------
    // DWORD IcmpSendEcho2Ex(
    //   HANDLE          IcmpHandle,
    //   HANDLE          Event,              // NULL
    //   FARPROC         ApcRoutine,         // NULL
    //   PVOID           ApcContext,         // NULL
    //   IPAddr          SourceAddress,      // DWORD (IPv4 src, may be 0)
    //   IPAddr          DestinationAddress, // DWORD (IPv4 dst, big-endian)
    //   LPVOID          RequestData,
    //   WORD            RequestSize,
    //   PIP_OPTION_INFORMATION RequestOptions, // pointer to struct (TTL, TOS …)
    //   LPVOID          ReplyBuffer,
    //   DWORD           ReplySize,
    //   DWORD           Timeout             // ms
    // )
    // -------------------------------------------------------------------------

    private static final MethodHandle MH_ICMP_SEND_ECHO2EX = LINKER.downcallHandle(
            IPHLPAPI.find("IcmpSendEcho2Ex").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,  // DWORD return (reply count)
                    ValueLayout.ADDRESS,   // IcmpHandle
                    ValueLayout.ADDRESS,   // Event (NULL)
                    ValueLayout.ADDRESS,   // ApcRoutine (NULL)
                    ValueLayout.ADDRESS,   // ApcContext (NULL)
                    ValueLayout.JAVA_INT,  // SourceAddress (IPAddr = DWORD)
                    ValueLayout.JAVA_INT,  // DestinationAddress (IPAddr = DWORD)
                    ValueLayout.ADDRESS,   // RequestData
                    ValueLayout.JAVA_SHORT,// RequestSize (WORD)
                    ValueLayout.ADDRESS,   // RequestOptions (IP_OPTION_INFORMATION*)
                    ValueLayout.ADDRESS,   // ReplyBuffer
                    ValueLayout.JAVA_INT,  // ReplySize
                    ValueLayout.JAVA_INT)  // Timeout (ms)
    );

    // -------------------------------------------------------------------------
    // DWORD Icmp6SendEcho2(
    //   HANDLE          IcmpHandle,
    //   HANDLE          Event,
    //   FARPROC         ApcRoutine,
    //   PVOID           ApcContext,
    //   sockaddr_in6*   SourceAddress,
    //   sockaddr_in6*   DestinationAddress,
    //   LPVOID          RequestData,
    //   WORD            RequestSize,
    //   PIP_OPTION_INFORMATION RequestOptions,
    //   LPVOID          ReplyBuffer,
    //   DWORD           ReplySize,
    //   DWORD           Timeout
    // )
    // -------------------------------------------------------------------------

    private static final MethodHandle MH_ICMP6_SEND_ECHO2 = LINKER.downcallHandle(
            IPHLPAPI.find("Icmp6SendEcho2").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,  // DWORD return
                    ValueLayout.ADDRESS,   // IcmpHandle
                    ValueLayout.ADDRESS,   // Event (NULL)
                    ValueLayout.ADDRESS,   // ApcRoutine (NULL)
                    ValueLayout.ADDRESS,   // ApcContext (NULL)
                    ValueLayout.ADDRESS,   // SourceAddress (sockaddr_in6*)
                    ValueLayout.ADDRESS,   // DestinationAddress (sockaddr_in6*)
                    ValueLayout.ADDRESS,   // RequestData
                    ValueLayout.JAVA_SHORT,// RequestSize
                    ValueLayout.ADDRESS,   // RequestOptions
                    ValueLayout.ADDRESS,   // ReplyBuffer
                    ValueLayout.JAVA_INT,  // ReplySize
                    ValueLayout.JAVA_INT)  // Timeout
    );

    // -------------------------------------------------------------------------
    // Private constructor – utility class
    // -------------------------------------------------------------------------

    private IcmpLib() {}

    // -------------------------------------------------------------------------
    // Public wrappers
    // -------------------------------------------------------------------------

    /**
     * Opens an IPv4 ICMP handle.
     *
     * @return handle segment; check {@link #isInvalidHandle} before use.
     */
    public static MemorySegment icmpCreateFile() {
        try {
            return (MemorySegment) MH_ICMP_CREATE_FILE.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException("IcmpCreateFile failed", t);
        }
    }

    /**
     * Opens an IPv6 ICMP handle.
     *
     * @return handle segment; check {@link #isInvalidHandle} before use.
     */
    public static MemorySegment icmp6CreateFile() {
        try {
            return (MemorySegment) MH_ICMP6_CREATE_FILE.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException("Icmp6CreateFile failed", t);
        }
    }

    /**
     * Closes an ICMP handle previously opened via {@link #icmpCreateFile()} or
     * {@link #icmp6CreateFile()}.
     *
     * @return non-zero on success
     */
    public static int icmpCloseHandle(MemorySegment icmpHandle) {
        try {
            return (int) MH_ICMP_CLOSE_HANDLE.invokeExact(icmpHandle);
        } catch (Throwable t) {
            throw new RuntimeException("IcmpCloseHandle failed", t);
        }
    }

    /**
     * Sends one IPv4 ICMP echo request and waits for a reply.
     *
     * @param icmpHandle     handle from {@link #icmpCreateFile()}
     * @param srcAddr        source IPv4 address as big-endian int (0 = any)
     * @param dstAddr        destination IPv4 address as big-endian int
     * @param requestData    payload segment
     * @param requestSize    payload length (WORD)
     * @param requestOptions IP_OPTION_INFORMATION segment (TTL, TOS, …)
     * @param replyBuffer    output buffer (must be pre-allocated)
     * @param replySize      size of reply buffer in bytes
     * @param timeoutMs      send timeout in milliseconds
     * @return number of replies received (0 = timeout / error)
     */
    public static int icmpSendEcho2Ex(MemorySegment icmpHandle,
                                      int srcAddr, int dstAddr,
                                      MemorySegment requestData, short requestSize,
                                      MemorySegment requestOptions,
                                      MemorySegment replyBuffer, int replySize,
                                      int timeoutMs) {
        try {
            return (int) MH_ICMP_SEND_ECHO2EX.invokeExact(
                    icmpHandle,
                    MemorySegment.NULL,  // Event
                    MemorySegment.NULL,  // ApcRoutine
                    MemorySegment.NULL,  // ApcContext
                    srcAddr,
                    dstAddr,
                    requestData,
                    requestSize,
                    requestOptions,
                    replyBuffer,
                    replySize,
                    timeoutMs);
        } catch (Throwable t) {
            throw new RuntimeException("IcmpSendEcho2Ex failed", t);
        }
    }

    /**
     * Sends one IPv6 ICMP echo request and waits for a reply.
     *
     * @param icmpHandle     handle from {@link #icmp6CreateFile()}
     * @param srcAddr        sockaddr_in6 segment for source (port=0 is fine)
     * @param dstAddr        sockaddr_in6 segment for destination
     * @param requestData    payload segment
     * @param requestSize    payload length (WORD)
     * @param requestOptions IP_OPTION_INFORMATION segment (Ttl field matters)
     * @param replyBuffer    output buffer
     * @param replySize      size of reply buffer in bytes
     * @param timeoutMs      send timeout in milliseconds
     * @return number of replies received
     */
    public static int icmp6SendEcho2(MemorySegment icmpHandle,
                                     MemorySegment srcAddr, MemorySegment dstAddr,
                                     MemorySegment requestData, short requestSize,
                                     MemorySegment requestOptions,
                                     MemorySegment replyBuffer, int replySize,
                                     int timeoutMs) {
        try {
            return (int) MH_ICMP6_SEND_ECHO2.invokeExact(
                    icmpHandle,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    srcAddr,
                    dstAddr,
                    requestData,
                    requestSize,
                    requestOptions,
                    replyBuffer,
                    replySize,
                    timeoutMs);
        } catch (Throwable t) {
            throw new RuntimeException("Icmp6SendEcho2 failed", t);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when the handle value equals
     * {@code INVALID_HANDLE_VALUE} (i.e., creation failed).
     */
    public static boolean isInvalidHandle(MemorySegment handle) {
        // On 64-bit Windows INVALID_HANDLE_VALUE is 0xFFFFFFFFFFFFFFFF.
        // MemorySegment.address() returns a raw long pointer value.
        return handle.address() == INVALID_HANDLE_VALUE || handle.equals(MemorySegment.NULL);
    }
}

