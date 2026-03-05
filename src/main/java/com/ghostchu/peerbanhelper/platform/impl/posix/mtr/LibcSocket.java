package com.ghostchu.peerbanhelper.platform.impl.posix.mtr;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

/**
 * FFM bindings for the POSIX libc socket API required for ICMP tracing.
 *
 * <p>All symbols are looked up through {@code Linker.nativeLinker().defaultLookup()}
 * so no explicit library name is needed (works on both Linux and macOS).
 *
 * <h3>Socket creation strategy</h3>
 * <ol>
 *   <li>Try {@code SOCK_DGRAM + IPPROTO_ICMP} (unprivileged on Linux ≥ 3.x with
 *       the correct {@code net.ipv4.ping_group_range} sysctl, and on macOS).</li>
 *   <li>If {@code socket()} returns {@code -1} with errno {@code EPERM} or
 *       {@code EACCES}, fall back to {@code SOCK_RAW + IPPROTO_ICMP} (requires
 *       {@code CAP_NET_RAW} or root).</li>
 *   <li>If that also fails, throw
 *       {@link com.ghostchu.peerbanhelper.platform.mtr.exception.MtrPermissionException}.</li>
 * </ol>
 */
public final class LibcSocket {

    // -------------------------------------------------------------------------
    // Linker / symbol lookup
    // -------------------------------------------------------------------------

    private static final Linker       LINKER  = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP  = LINKER.defaultLookup();

    // =========================================================================
    // int socket(int domain, int type, int protocol)
    // =========================================================================

    private static final MethodHandle MH_SOCKET = LINKER.downcallHandle(
            LOOKUP.find("socket").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,   // domain
                    ValueLayout.JAVA_INT,   // type
                    ValueLayout.JAVA_INT)   // protocol
    );

    // =========================================================================
    // int close(int fd)
    // =========================================================================

    private static final MethodHandle MH_CLOSE = LINKER.downcallHandle(
            LOOKUP.find("close").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT)   // fd
    );

    // =========================================================================
    // int setsockopt(int sockfd, int level, int optname,
    //                const void *optval, socklen_t optlen)
    // =========================================================================

    private static final MethodHandle MH_SETSOCKOPT = LINKER.downcallHandle(
            LOOKUP.find("setsockopt").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,   // sockfd
                    ValueLayout.JAVA_INT,   // level
                    ValueLayout.JAVA_INT,   // optname
                    ValueLayout.ADDRESS,    // optval
                    ValueLayout.JAVA_INT)   // optlen
    );

    // =========================================================================
    // ssize_t sendto(int sockfd, const void *buf, size_t len, int flags,
    //                const struct sockaddr *dest_addr, socklen_t addrlen)
    // =========================================================================

    private static final MethodHandle MH_SENDTO = LINKER.downcallHandle(
            LOOKUP.find("sendto").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG,  // ssize_t
                    ValueLayout.JAVA_INT,   // sockfd
                    ValueLayout.ADDRESS,    // buf
                    ValueLayout.JAVA_LONG,  // len (size_t)
                    ValueLayout.JAVA_INT,   // flags
                    ValueLayout.ADDRESS,    // dest_addr
                    ValueLayout.JAVA_INT)   // addrlen
    );

    // =========================================================================
    // ssize_t recvfrom(int sockfd, void *buf, size_t len, int flags,
    //                  struct sockaddr *src_addr, socklen_t *addrlen)
    // =========================================================================

    private static final MethodHandle MH_RECVFROM = LINKER.downcallHandle(
            LOOKUP.find("recvfrom").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG,  // ssize_t
                    ValueLayout.JAVA_INT,   // sockfd
                    ValueLayout.ADDRESS,    // buf
                    ValueLayout.JAVA_LONG,  // len (size_t)
                    ValueLayout.JAVA_INT,   // flags
                    ValueLayout.ADDRESS,    // src_addr
                    ValueLayout.ADDRESS)    // addrlen (socklen_t*)
    );

    // =========================================================================
    // int bind(int sockfd, const struct sockaddr *addr, socklen_t addrlen)
    // =========================================================================

    private static final MethodHandle MH_BIND = LINKER.downcallHandle(
            LOOKUP.find("bind").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT)
    );

    // =========================================================================
    // int select(int nfds, fd_set *readfds, fd_set *writefds,
    //            fd_set *exceptfds, struct timeval *timeout)
    // =========================================================================

    private static final MethodHandle MH_SELECT = LINKER.downcallHandle(
            LOOKUP.find("select").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,   // nfds
                    ValueLayout.ADDRESS,    // readfds
                    ValueLayout.ADDRESS,    // writefds
                    ValueLayout.ADDRESS,    // exceptfds
                    ValueLayout.ADDRESS)    // timeout
    );

    // =========================================================================
    // errno access
    // =========================================================================

    /**
     * On Linux errno is accessed through a thread-local pointer returned by
     * {@code __errno_location()}; on macOS via {@code __error()}.  Both return
     * {@code int*}.
     */
    private static final MethodHandle MH_ERRNO_LOCATION;

    static {
        Optional<MemorySegment> errnoSym = LOOKUP.find("__errno_location"); // Linux
        if (errnoSym.isEmpty()) {
            errnoSym = LOOKUP.find("__error"); // macOS
        }
        if (errnoSym.isPresent()) {
            MH_ERRNO_LOCATION = LINKER.downcallHandle(
                    errnoSym.get(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS) // int*
            );
        } else {
            MH_ERRNO_LOCATION = null; // should not happen on Linux/macOS
        }
    }

    // -------------------------------------------------------------------------
    // Private constructor
    // -------------------------------------------------------------------------

    private LibcSocket() {}

    // =========================================================================
    // Public wrappers
    // =========================================================================

    /**
     * Creates a socket.
     *
     * @return file descriptor (≥ 0) on success, {@code -1} on error (check errno)
     */
    public static int socket(int domain, int type, int protocol) {
        try {
            return (int) MH_SOCKET.invokeExact(domain, type, protocol);
        } catch (Throwable t) {
            throw new RuntimeException("socket() failed", t);
        }
    }

    /**
     * Closes a socket file descriptor.
     *
     * @return {@code 0} on success, {@code -1} on error
     */
    public static int close(int fd) {
        try {
            return (int) MH_CLOSE.invokeExact(fd);
        } catch (Throwable t) {
            throw new RuntimeException("close() failed", t);
        }
    }

    /**
     * Sets a socket option.
     *
     * @param optval  pre-allocated segment holding the option value
     * @param optlen  byte length of optval
     * @return {@code 0} on success, {@code -1} on error
     */
    public static int setsockopt(int fd, int level, int optname,
                                 MemorySegment optval, int optlen) {
        try {
            return (int) MH_SETSOCKOPT.invokeExact(fd, level, optname, optval, optlen);
        } catch (Throwable t) {
            throw new RuntimeException("setsockopt() failed", t);
        }
    }

    /**
     * Sends a datagram.
     *
     * @param buf      payload segment
     * @param len      number of bytes to send
     * @param flags    send flags (normally 0)
     * @param destAddr destination sockaddr
     * @param addrLen  byte length of destAddr
     * @return bytes sent, or {@code -1} on error
     */
    public static long sendto(int fd, MemorySegment buf, long len, int flags,
                              MemorySegment destAddr, int addrLen) {
        try {
            return (long) MH_SENDTO.invokeExact(fd, buf, len, flags, destAddr, addrLen);
        } catch (Throwable t) {
            throw new RuntimeException("sendto() failed", t);
        }
    }

    /**
     * Receives a datagram.
     *
     * @param buf       receive buffer
     * @param len       buffer capacity
     * @param flags     receive flags (normally 0)
     * @param srcAddr   output sockaddr (may be {@link MemorySegment#NULL})
     * @param addrLenPtr output socklen_t (may be {@link MemorySegment#NULL})
     * @return bytes received, or {@code -1} on error
     */
    public static long recvfrom(int fd, MemorySegment buf, long len, int flags,
                                MemorySegment srcAddr, MemorySegment addrLenPtr) {
        try {
            return (long) MH_RECVFROM.invokeExact(fd, buf, len, flags, srcAddr, addrLenPtr);
        } catch (Throwable t) {
            throw new RuntimeException("recvfrom() failed", t);
        }
    }

    /**
     * Binds a socket to a local address.
     *
     * @return {@code 0} on success, {@code -1} on error
     */
    public static int bind(int fd, MemorySegment addr, int addrLen) {
        try {
            return (int) MH_BIND.invokeExact(fd, addr, addrLen);
        } catch (Throwable t) {
            throw new RuntimeException("bind() failed", t);
        }
    }

    /**
     * Waits until {@code fd} becomes readable or timeout expires.
     *
     * @param fd        socket file descriptor
     * @param timeoutUs timeout in microseconds (0 = poll, negative = ignored/use 0)
     * @return {@code 1} if readable, {@code 0} on timeout, {@code -1} on error
     */
    public static int selectReadable(int fd, long timeoutUs) {
        try (Arena arena = Arena.ofConfined()) {
            // fd_set: 128 bytes on Linux (1024 bits), 128 bytes on macOS
            int fdSetBytes = 128;
            MemorySegment fdSet = arena.allocate(fdSetBytes);

            // FD_ZERO + FD_SET: set bit [fd] in fdSet
            int wordIndex = fd / 64;
            long bitMask  = 1L << (fd % 64);
            if (wordIndex < fdSetBytes / 8) {
                long current = fdSet.get(ValueLayout.JAVA_LONG, (long) wordIndex * 8);
                fdSet.set(ValueLayout.JAVA_LONG, (long) wordIndex * 8, current | bitMask);
            }

            // struct timeval { long tv_sec; long tv_usec; }
            // On Linux/macOS 64-bit: each field is 8 bytes
            MemorySegment tv = arena.allocate(16); // 8 + 8
            long secs  = timeoutUs / 1_000_000L;
            long usecs = timeoutUs % 1_000_000L;
            tv.set(ValueLayout.JAVA_LONG, 0, secs);
            tv.set(ValueLayout.JAVA_LONG, 8, usecs);

            return (int) MH_SELECT.invokeExact(fd + 1, fdSet, MemorySegment.NULL,
                                               MemorySegment.NULL, tv);
        } catch (Throwable t) {
            throw new RuntimeException("select() failed", t);
        }
    }

    /**
     * Reads the current value of {@code errno} for the calling thread.
     *
     * @return errno value, or {@code -1} if errno location is unavailable
     */
    public static int errno() {
        if (MH_ERRNO_LOCATION == null) return -1;
        try {
            MemorySegment ptr = (MemorySegment) MH_ERRNO_LOCATION.invokeExact();
            if (ptr.equals(MemorySegment.NULL)) return -1;
            // Reinterpret as a single int (errno is an int)
            return ptr.reinterpret(4).get(ValueLayout.JAVA_INT, 0);
        } catch (Throwable t) {
            return -1;
        }
    }

    // =========================================================================
    // Constants (not platform-dependent for our use-cases)
    // =========================================================================

    /** Address family: IPv4. */
    public static final int AF_INET    = 2;
    /** Address family: IPv6. */
    public static final int AF_INET6   = 10; // Linux; overridden for macOS at runtime

    /** Socket type: raw (requires privileges). */
    public static final int SOCK_RAW   = 3;
    /** Socket type: datagram (unprivileged ICMP on Linux/macOS). */
    public static final int SOCK_DGRAM = 2;

    /** IP protocol: ICMPv4. */
    public static final int IPPROTO_ICMP   = 1;
    /** IP protocol: ICMPv6. */
    public static final int IPPROTO_ICMPV6 = 58;

    /** SOL_SOCKET level. */
    public static final int SOL_SOCKET      = 1;
    /** IPPROTO_IP level. */
    public static final int IPPROTO_IP      = 0;

    /* ----- IP_* socket options ----- */
    /** IP_TTL (Linux/macOS). */
    public static final int IP_TTL          = 2;
    /** IP_TOS (Linux). */
    public static final int IP_TOS          = 1;
    /** IPV6_UNICAST_HOPS. */
    public static final int IPV6_UNICAST_HOPS = 16;
    /** IPV6_TCLASS (Linux). */
    public static final int IPV6_TCLASS     = 67;

    /** SO_RCVTIMEO – receive timeout. */
    public static final int SO_RCVTIMEO     = 20; // Linux; 20 for macOS too

    /** errno: Operation not permitted. */
    public static final int EPERM  = 1;
    /** errno: Permission denied. */
    public static final int EACCES = 13;
    /** errno: Would block / timed out (non-blocking or SO_RCVTIMEO). */
    public static final int EAGAIN  = 11;
    public static final int EWOULDBLOCK = 11;

    /** sizeof(struct sockaddr_in)  = 16 bytes. */
    public static final int SOCKADDR_IN_SIZE  = 16;
    /** sizeof(struct sockaddr_in6) = 28 bytes. */
    public static final int SOCKADDR_IN6_SIZE = 28;

    // sockaddr_in offsets
    public static final int SA_OFFSET_FAMILY = 0;  // 2 bytes
    public static final int SA_OFFSET_PORT   = 2;  // 2 bytes
    public static final int SA_OFFSET_ADDR   = 4;  // 4 bytes

    // sockaddr_in6 offsets
    public static final int SA6_OFFSET_FAMILY   = 0;   // 2 bytes
    public static final int SA6_OFFSET_PORT     = 2;   // 2 bytes
    public static final int SA6_OFFSET_FLOWINFO = 4;   // 4 bytes
    public static final int SA6_OFFSET_ADDR     = 8;   // 16 bytes
    public static final int SA6_OFFSET_SCOPE_ID = 24;  // 4 bytes
}

