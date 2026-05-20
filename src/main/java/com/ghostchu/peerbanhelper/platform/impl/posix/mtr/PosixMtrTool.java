package com.ghostchu.peerbanhelper.platform.impl.posix.mtr;

import com.ghostchu.peerbanhelper.platform.mtr.MtrHop;
import com.ghostchu.peerbanhelper.platform.mtr.MtrOptions;
import com.ghostchu.peerbanhelper.platform.mtr.MtrResult;
import com.ghostchu.peerbanhelper.platform.mtr.exception.MtrException;
import com.ghostchu.peerbanhelper.platform.mtr.exception.MtrPermissionException;
import com.ghostchu.peerbanhelper.platform.mtr.exception.MtrTimeoutException;
import com.ghostchu.peerbanhelper.platform.mtr.exception.MtrUnsupportedException;
import com.ghostchu.peerbanhelper.platform.types.MtrTool;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * POSIX (Linux / macOS) implementation of {@link MtrTool} using raw ICMP sockets
 * via the Foreign Function & Memory API.
 *
 * <h3>Socket creation strategy</h3>
 * <ol>
 *   <li>Try {@code SOCK_DGRAM + IPPROTO_ICMP(V6)} – works without root on Linux
 *       (if {@code net.ipv4.ping_group_range} allows the current GID) and macOS.</li>
 *   <li>On {@code EPERM} / {@code EACCES}, try {@code SOCK_RAW + IPPROTO_ICMP(V6)}.</li>
 *   <li>If that also fails, throw {@link MtrPermissionException}.</li>
 * </ol>
 *
 * <h3>IPv6 / macOS caveat</h3>
 * macOS supports {@code SOCK_DGRAM + IPPROTO_ICMPV6} only for link-local or with
 * root.  When the DGRAM approach fails for IPv6 on macOS, we automatically retry
 * with {@code SOCK_RAW}.
 *
 * <h3>Concurrency</h3>
 * All TTL levels are probed in parallel using virtual threads.  Within each TTL,
 * probes are sent sequentially and retried up to {@link MtrOptions#getRetryCount()}
 * times on timeout before being marked as lost.
 *
 * <h3>Identifier disambiguation</h3>
 * Each {@link PosixMtrTool} instance uses a random 16-bit {@code identifier}
 * embedded in the ICMP echo header so that concurrent instances (or external ping
 * processes) do not interfere with each other.
 */
@Slf4j
public final class PosixMtrTool implements MtrTool {

    // -------------------------------------------------------------------------
    // Instance state
    // -------------------------------------------------------------------------

    /** Mode used to open the IPv4 socket (null if IPv4 is unavailable). */
    @Nullable private final PosixSocketMode mode4;
    /** Mode used to open the IPv6 socket (null if IPv6 is unavailable). */
    @Nullable private final PosixSocketMode mode6;

    /** Reason IPv4 is unavailable, if any. */
    @Nullable private final String unavailReason4;
    /** Reason IPv6 is unavailable, if any. */
    @Nullable private final String unavailReason6;

    /**
     * Per-instance unique identifier (16-bit) for ICMP echo frames.
     * Avoids mixing up replies with other probing processes.
     */
    private final int identifier;

    /** Sequence counter – incremented per probe send. */
    private final AtomicInteger seqCounter = new AtomicInteger(0);

    /** Whether we are running on macOS (affects AF_INET6 constant). */
    private final boolean isMacOs;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@link PosixMtrTool}, probing socket availability for both
     * IPv4 and IPv6.
     *
     * <p>This constructor does <em>not</em> throw – unavailability of one or both
     * address families is recorded internally and surfaced via
     * {@link #isSupported(InetAddress)}.
     */
    public PosixMtrTool() {
        this.identifier = ThreadLocalRandom.current().nextInt(1, 0xFFFF);
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        this.isMacOs  = osName.contains("mac");

        // Probe IPv4
        SocketProbeResult r4 = probeSocketAvailability(false);
        this.mode4          = r4.mode;
        this.unavailReason4 = r4.errorReason;
        if (r4.mode != null) {
            log.debug("MTR IPv4 socket mode: {}", r4.mode);
        } else {
            log.debug("MTR IPv4 not available: {}", r4.errorReason);
        }

        // Probe IPv6
        SocketProbeResult r6 = probeSocketAvailability(true);
        this.mode6          = r6.mode;
        this.unavailReason6 = r6.errorReason;
        if (r6.mode != null) {
            log.debug("MTR IPv6 socket mode: {}", r6.mode);
        } else {
            log.debug("MTR IPv6 not available: {}", r6.errorReason);
        }
    }

    // -------------------------------------------------------------------------
    // MtrTool interface
    // -------------------------------------------------------------------------

    @Override
    public boolean isSupported(@NotNull InetAddress target) {
        if (target instanceof Inet4Address) return mode4 != null;
        if (target instanceof Inet6Address) return mode6 != null;
        return false;
    }

    @Override
    public @NotNull MtrResult trace(@NotNull InetAddress target, @NotNull MtrOptions options)
            throws MtrException {

        boolean isV6 = target instanceof Inet6Address;
        PosixSocketMode mode = isV6 ? mode6 : mode4;
        String unavailReason = isV6 ? unavailReason6 : unavailReason4;

        if (mode == null) {
            // Determine the right exception type
            if (unavailReason != null && unavailReason.contains("errno=" + LibcSocket.EPERM)
                    || unavailReason != null && unavailReason.contains("errno=" + LibcSocket.EACCES)) {
                throw new MtrPermissionException(
                        "Insufficient privileges to open ICMP socket: " + unavailReason,
                        LibcSocket.EPERM);
            }
            throw new MtrUnsupportedException(
                    "ICMP socket not available for "
                    + (isV6 ? "IPv6" : "IPv4") + ": " + unavailReason);
        }

        Instant start = Instant.now();
        byte[] payload = buildPayload(options.getPayloadSize());

        // Launch all TTL probes in parallel using virtual threads
        List<Future<MtrHop>> futures = new ArrayList<>(options.getMaxHops());
        long totalMs = options.getTotalTimeout().toMillis();

        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int ttl = 1; ttl <= options.getMaxHops(); ttl++) {
                final int currentTtl = ttl;
                futures.add(exec.submit(
                        () -> probeHop(target, currentTtl, payload, options, isV6, mode)));
            }
            exec.shutdown();
            try {
                if (!exec.awaitTermination(totalMs, TimeUnit.MILLISECONDS)) {
                    exec.shutdownNow();
                    throw new MtrTimeoutException(
                            "Total trace timeout exceeded (" + options.getTotalTimeout() + ")");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MtrException("Trace interrupted", e);
            }
        }

        // Collect results in TTL order, stopping at destination
        List<MtrHop> hops = new ArrayList<>(options.getMaxHops());
        for (int i = 0; i < options.getMaxHops(); i++) {
            MtrHop hop;
            try {
                hop = futures.get(i).get();
            } catch (ExecutionException e) {
                log.debug("TTL={} threw: {}", i + 1, e.getCause().getMessage());
                hop = unresponsiveHop(i + 1, options.getProbeCount());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MtrException("Interrupted collecting hop " + (i + 1), e);
            } catch (CancellationException e) {
                hop = unresponsiveHop(i + 1, options.getProbeCount());
            }
            hops.add(hop);

            // Stop once we've reached the destination
            if (hop.getAddress() != null && hop.getAddress().equals(target)) {
                break;
            }
        }

        // Optional PTR resolution
        if (options.isResolveHostnames()) {
            hops = resolveHostnames(hops);
        }

        return new MtrResult(target, hops, start, Duration.between(start, Instant.now()));
    }

    // -------------------------------------------------------------------------
    // Per-hop probing
    // -------------------------------------------------------------------------

    private MtrHop probeHop(InetAddress target, int ttl, byte[] payload,
                            MtrOptions options, boolean isV6,
                            PosixSocketMode mode) throws MtrException {
        List<Duration> rtts = new ArrayList<>(options.getProbeCount());
        int sent = 0, received = 0;
        InetAddress replyAddr = null;

        for (int probe = 0; probe < options.getProbeCount(); probe++) {
            sent++;
            Duration rtt = null;

            for (int attempt = 0; attempt <= options.getRetryCount(); attempt++) {
                long attemptTimeoutUs = computeAttemptTimeoutUs(options);
                ProbeResult res = sendSingleProbe(
                        target, ttl, payload, attemptTimeoutUs, isV6, mode);
                if (res != null) {
                    rtt = res.rtt;
                    if (replyAddr == null) replyAddr = res.from;
                    break;
                }
                // else: timed out – retry
            }

            if (rtt != null) {
                rtts.add(rtt);
                received++;
            }
        }

        return new MtrHop(ttl, replyAddr, null, rtts, sent, received);
    }

    /**
     * Per-attempt timeout in microseconds.  Divides the probe budget evenly
     * across {@code (1 + retryCount)} attempts.
     */
    private long computeAttemptTimeoutUs(MtrOptions options) {
        long budgetUs = options.getProbeTimeout().toNanos() / 1_000L;
        return Math.max(100_000L, budgetUs / (options.getRetryCount() + 1));
    }

    // -------------------------------------------------------------------------
    // Single probe (open socket, set TTL, send, recv)
    // -------------------------------------------------------------------------

    /**
     * Opens a fresh socket for each probe so that TTL option can be set cleanly
     * and so concurrent virtual threads don't share socket state.
     */
    @Nullable
    private ProbeResult sendSingleProbe(InetAddress target, int ttl, byte[] payload,
                                        long timeoutUs, boolean isV6,
                                        PosixSocketMode mode) throws MtrException {
        int af       = isV6 ? getAf6() : LibcSocket.AF_INET;
        int type     = (mode == PosixSocketMode.DGRAM_UNPRIVILEGED)
                       ? LibcSocket.SOCK_DGRAM : LibcSocket.SOCK_RAW;
        int protocol = isV6 ? LibcSocket.IPPROTO_ICMPV6 : LibcSocket.IPPROTO_ICMP;

        int fd = LibcSocket.socket(af, type, protocol);
        if (fd < 0) {
            int err = LibcSocket.errno();
            throw new MtrException(
                    "socket() failed for probe, errno=" + err + " (af=" + af + ", type=" + type + ")");
        }
        try {
            configureSendSocket(fd, ttl, isV6, timeoutUs);
            return doSendRecv(fd, target, payload, timeoutUs, isV6, mode);
        } finally {
            LibcSocket.close(fd);
        }
    }

    // -------------------------------------------------------------------------
    // Socket configuration
    // -------------------------------------------------------------------------

    private void configureSendSocket(int fd, int ttl, boolean isV6, long timeoutUs)
            throws MtrException {
        if (isV6) {
            setIntSockOpt(fd, LibcSocket.IPPROTO_ICMPV6, LibcSocket.IPV6_UNICAST_HOPS, ttl);
        } else {
            setIntSockOpt(fd, LibcSocket.IPPROTO_IP, LibcSocket.IP_TTL, ttl);
        }

        // Set SO_RCVTIMEO so recvfrom() returns EAGAIN on timeout
        try (Arena arena = Arena.ofConfined()) {
            // struct timeval { long sec; long usec; } – 16 bytes on 64-bit
            MemorySegment tv = arena.allocate(16);
            long secs  = timeoutUs / 1_000_000L;
            long usecs = timeoutUs % 1_000_000L;
            tv.set(ValueLayout.JAVA_LONG, 0, secs);
            tv.set(ValueLayout.JAVA_LONG, 8, usecs);
            int rc = LibcSocket.setsockopt(fd, LibcSocket.SOL_SOCKET,
                                           LibcSocket.SO_RCVTIMEO, tv, 16);
            if (rc != 0) {
                log.debug("setsockopt SO_RCVTIMEO failed, errno={}", LibcSocket.errno());
            }
        }
    }

    private void setIntSockOpt(int fd, int level, int optname, int value)
            throws MtrException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment valSeg = arena.allocate(ValueLayout.JAVA_INT);
            valSeg.set(ValueLayout.JAVA_INT, 0, value);
            int rc = LibcSocket.setsockopt(fd, level, optname, valSeg,
                                           (int) ValueLayout.JAVA_INT.byteSize());
            if (rc != 0) {
                int err = LibcSocket.errno();
                throw new MtrException(
                        "setsockopt(" + level + "," + optname + "=" + value + ") failed, errno=" + err);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Send / receive
    // -------------------------------------------------------------------------

    @Nullable
    private ProbeResult doSendRecv(int fd, InetAddress target,
                                   byte[] payload, long timeoutUs,
                                   boolean isV6, PosixSocketMode mode)
            throws MtrException {
        int seq = seqCounter.incrementAndGet() & 0xFFFF;

        try (Arena arena = Arena.ofConfined()) {
            // Build ICMP packet
            byte[] pkt = isV6
                    ? buildIcmpV6Packet(payload, identifier, seq)
                    : buildIcmpV4Packet(payload, identifier, seq);

            MemorySegment pktSeg = arena.allocate(pkt.length);
            MemorySegment.copy(pkt, 0, pktSeg, ValueLayout.JAVA_BYTE, 0, pkt.length);

            // Build destination sockaddr
            MemorySegment destAddr;
            int destAddrLen;
            if (isV6) {
                destAddr    = buildSockAddrIn6(arena, target.getAddress());
                destAddrLen = LibcSocket.SOCKADDR_IN6_SIZE;
            } else {
                destAddr    = buildSockAddrIn4(arena, target.getAddress());
                destAddrLen = LibcSocket.SOCKADDR_IN_SIZE;
            }

            long tSend = System.nanoTime();
            long sent  = LibcSocket.sendto(fd, pktSeg, pkt.length, 0,
                                           destAddr, destAddrLen);
            if (sent < 0) {
                int err = LibcSocket.errno();
                throw new MtrException("sendto() failed, errno=" + err);
            }

            // Wait for reply
            int ready = LibcSocket.selectReadable(fd, timeoutUs);
            if (ready <= 0) {
                return null; // timeout
            }

            // Receive reply
            int recvBufSize = 1500;
            MemorySegment recvBuf = arena.allocate(recvBufSize);
            MemorySegment srcAddrSeg = arena.allocate(
                    isV6 ? LibcSocket.SOCKADDR_IN6_SIZE : LibcSocket.SOCKADDR_IN_SIZE);
            MemorySegment addrLenPtr = arena.allocate(ValueLayout.JAVA_INT);
            addrLenPtr.set(ValueLayout.JAVA_INT, 0,
                           isV6 ? LibcSocket.SOCKADDR_IN6_SIZE : LibcSocket.SOCKADDR_IN_SIZE);

            long rcvLen = LibcSocket.recvfrom(
                    fd, recvBuf, recvBufSize, 0, srcAddrSeg, addrLenPtr);
            if (rcvLen < 0) {
                return null; // EAGAIN or error
            }
            long tRecv = System.nanoTime();

            // Parse reply and extract source address
            return parseReply(recvBuf, rcvLen, srcAddrSeg, tRecv - tSend, isV6, mode, identifier, seq);
        }
    }

    // -------------------------------------------------------------------------
    // ICMP packet builders
    // -------------------------------------------------------------------------

    /**
     * Builds an ICMPv4 echo-request packet with checksum.
     * <pre>
     * byte  0: type (8 = ECHO REQUEST)
     * byte  1: code (0)
     * byte  2-3: checksum (ones complement)
     * byte  4-5: identifier
     * byte  6-7: sequence
     * byte  8+:  payload
     * </pre>
     */
    private static byte[] buildIcmpV4Packet(byte[] payload, int id, int seq) {
        byte[] pkt = new byte[8 + payload.length];
        pkt[0] = 8;                         // ICMP_ECHO
        pkt[1] = 0;
        pkt[2] = 0; pkt[3] = 0;             // checksum placeholder
        pkt[4] = (byte) (id  >> 8);
        pkt[5] = (byte)  id;
        pkt[6] = (byte) (seq >> 8);
        pkt[7] = (byte)  seq;
        System.arraycopy(payload, 0, pkt, 8, payload.length);
        int csum = icmpChecksum(pkt);
        pkt[2] = (byte) (csum >> 8);
        pkt[3] = (byte)  csum;
        return pkt;
    }

    /**
     * Builds an ICMPv6 echo-request packet (checksum is computed by kernel for
     * SOCK_RAW; for SOCK_DGRAM the kernel also fills it in, but we compute it
     * ourselves for SOCK_RAW to be safe).
     * <pre>
     * byte  0: type (128 = ICMPV6_ECHO_REQUEST)
     * byte  1: code (0)
     * byte  2-3: checksum (0 – kernel fills for SOCK_DGRAM, we fill for SOCK_RAW)
     * byte  4-5: identifier
     * byte  6-7: sequence
     * byte  8+:  payload
     * </pre>
     * For SOCK_RAW the kernel handles the pseudo-header checksum via
     * IPV6_CHECKSUM socket option; we leave checksum = 0 here and rely on the
     * kernel to fill it.
     */
    private static byte[] buildIcmpV6Packet(byte[] payload, int id, int seq) {
        byte[] pkt = new byte[8 + payload.length];
        pkt[0] = (byte) 128;                // ICMPV6_ECHO_REQUEST
        pkt[1] = 0;
        pkt[2] = 0; pkt[3] = 0;             // checksum – kernel fills
        pkt[4] = (byte) (id  >> 8);
        pkt[5] = (byte)  id;
        pkt[6] = (byte) (seq >> 8);
        pkt[7] = (byte)  seq;
        System.arraycopy(payload, 0, pkt, 8, payload.length);
        return pkt;
    }

    /** Standard ICMP ones-complement checksum. */
    private static int icmpChecksum(byte[] data) {
        int sum = 0;
        for (int i = 0; i < data.length - 1; i += 2) {
            sum += ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
        }
        if ((data.length & 1) == 1) {
            sum += (data[data.length - 1] & 0xFF) << 8;
        }
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return ~sum & 0xFFFF;
    }

    // -------------------------------------------------------------------------
    // Reply parsing
    // -------------------------------------------------------------------------

    /**
     * Parses the raw bytes received from the socket to extract the source
     * address and RTT.
     *
     * <p>For SOCK_RAW with IPv4: the kernel prepends the 20-byte IP header,
     * so the ICMP header starts at byte 20.  The ICMP type will be either
     * 0 (echo reply) or 11 (time exceeded – contains the original IP+ICMP).
     *
     * <p>For SOCK_DGRAM with IPv4/IPv6: the kernel strips the IP header and
     * delivers only the ICMP payload starting from byte 0.
     */
    @Nullable
    private ProbeResult parseReply(MemorySegment buf, long rcvLen,
                                   MemorySegment srcAddrSeg,
                                   long elapsedNs,
                                   boolean isV6, PosixSocketMode mode,
                                   int expectedId, int expectedSeq)
            throws MtrException {
        try {
            byte[] raw = segmentToBytes(buf, rcvLen);

            // Extract source IP from sockaddr
            InetAddress from = isV6
                    ? extractAddrV6(srcAddrSeg)
                    : extractAddrV4(srcAddrSeg);

            // Locate the ICMP header within the received bytes
            int icmpOffset;
            if (!isV6 && mode == PosixSocketMode.RAW_PRIVILEGED) {
                // IPv4 RAW: kernel delivers IP header + ICMP
                if (raw.length < 20) return null;
                icmpOffset = (raw[0] & 0x0F) * 4; // IP header length from IHL field
            } else {
                // IPv4 DGRAM or IPv6 (any mode): kernel strips IP header
                icmpOffset = 0;
            }

            if (raw.length < icmpOffset + 8) return null;

            int icmpType = raw[icmpOffset] & 0xFF;
            int icmpCode = raw[icmpOffset + 1] & 0xFF;

            if (isV6) {
                // ICMPv6 type 129 = echo reply; type 3 = time exceeded
                if (icmpType == 129) {
                    // Echo reply – check identifier and sequence
                    int replyId  = ((raw[icmpOffset + 4] & 0xFF) << 8) | (raw[icmpOffset + 5] & 0xFF);
                    int replySeq = ((raw[icmpOffset + 6] & 0xFF) << 8) | (raw[icmpOffset + 7] & 0xFF);
                    if (replyId != expectedId || replySeq != expectedSeq) return null; // not ours
                    return new ProbeResult(from, Duration.ofNanos(elapsedNs));
                } else if (icmpType == 3 && icmpCode == 0) {
                    // Time exceeded (hop limit) – valid intermediate hop
                    return new ProbeResult(from, Duration.ofNanos(elapsedNs));
                }
            } else {
                // ICMPv4 type 0 = echo reply; type 11 = time exceeded
                if (icmpType == 0) {
                    // Echo reply – check identifier and sequence
                    int replyId  = ((raw[icmpOffset + 4] & 0xFF) << 8) | (raw[icmpOffset + 5] & 0xFF);
                    int replySeq = ((raw[icmpOffset + 6] & 0xFF) << 8) | (raw[icmpOffset + 7] & 0xFF);
                    if (replyId != expectedId || replySeq != expectedSeq) return null;
                    return new ProbeResult(from, Duration.ofNanos(elapsedNs));
                } else if (icmpType == 11) {
                    // Time exceeded – intermediate hop; valid for MTR
                    return new ProbeResult(from, Duration.ofNanos(elapsedNs));
                } else if (icmpType == 3) {
                    // Destination unreachable – still a valid hop response
                    return new ProbeResult(from, Duration.ofNanos(elapsedNs));
                }
            }

            return null; // unexpected ICMP type
        } catch (UnknownHostException e) {
            throw new MtrException("Failed to parse reply address", e);
        }
    }

    // -------------------------------------------------------------------------
    // sockaddr builders
    // -------------------------------------------------------------------------

    private static MemorySegment buildSockAddrIn4(Arena arena, byte[] ipv4) {
        MemorySegment seg = arena.allocate(LibcSocket.SOCKADDR_IN_SIZE);
        // sin_family = AF_INET (2), little-endian short
        seg.set(ValueLayout.JAVA_SHORT, LibcSocket.SA_OFFSET_FAMILY, (short) LibcSocket.AF_INET);
        seg.set(ValueLayout.JAVA_SHORT, LibcSocket.SA_OFFSET_PORT, (short) 0);
        MemorySegment.copy(ipv4, 0, seg, ValueLayout.JAVA_BYTE, LibcSocket.SA_OFFSET_ADDR, 4);
        return seg;
    }

    private MemorySegment buildSockAddrIn6(Arena arena, byte[] ipv6) {
        MemorySegment seg = arena.allocate(LibcSocket.SOCKADDR_IN6_SIZE);
        seg.set(ValueLayout.JAVA_SHORT, LibcSocket.SA6_OFFSET_FAMILY, (short) getAf6());
        seg.set(ValueLayout.JAVA_SHORT, LibcSocket.SA6_OFFSET_PORT, (short) 0);
        seg.set(ValueLayout.JAVA_INT,   LibcSocket.SA6_OFFSET_FLOWINFO, 0);
        MemorySegment.copy(ipv6, 0, seg, ValueLayout.JAVA_BYTE, LibcSocket.SA6_OFFSET_ADDR, 16);
        seg.set(ValueLayout.JAVA_INT, LibcSocket.SA6_OFFSET_SCOPE_ID, 0);
        return seg;
    }

    // -------------------------------------------------------------------------
    // Address extraction from received sockaddr
    // -------------------------------------------------------------------------

    private static InetAddress extractAddrV4(MemorySegment sockAddr)
            throws UnknownHostException {
        byte[] addr = new byte[4];
        MemorySegment.copy(sockAddr, ValueLayout.JAVA_BYTE,
                           LibcSocket.SA_OFFSET_ADDR, addr, 0, 4);
        return InetAddress.getByAddress(addr);
    }

    private static InetAddress extractAddrV6(MemorySegment sockAddr)
            throws UnknownHostException {
        byte[] addr = new byte[16];
        MemorySegment.copy(sockAddr, ValueLayout.JAVA_BYTE,
                           LibcSocket.SA6_OFFSET_ADDR, addr, 0, 16);
        return InetAddress.getByAddress(addr);
    }

    // -------------------------------------------------------------------------
    // Socket availability probe (used at construction)
    // -------------------------------------------------------------------------

    /**
     * Tries to open a test socket (immediately closed) to determine which mode
     * is available.
     *
     * @param isV6 {@code true} to test IPv6
     * @return result containing mode or error reason
     */
    private SocketProbeResult probeSocketAvailability(boolean isV6) {
        int af       = isV6 ? getAf6() : LibcSocket.AF_INET;
        int protocol = isV6 ? LibcSocket.IPPROTO_ICMPV6 : LibcSocket.IPPROTO_ICMP;

        // On macOS + IPv6, skip DGRAM attempt (unreliable without root)
        if (!isMacOs || !isV6) {
            int fd = LibcSocket.socket(af, LibcSocket.SOCK_DGRAM, protocol);
            if (fd >= 0) {
                LibcSocket.close(fd);
                return SocketProbeResult.ok(PosixSocketMode.DGRAM_UNPRIVILEGED);
            }
            int err = LibcSocket.errno();
            log.debug("SOCK_DGRAM ICMP{} not available, errno={}", isV6 ? "V6" : "", err);
            if (err != LibcSocket.EPERM && err != LibcSocket.EACCES) {
                return SocketProbeResult.fail("SOCK_DGRAM failed with errno=" + err);
            }
        }

        // Try RAW
        int fd = LibcSocket.socket(af, LibcSocket.SOCK_RAW, protocol);
        if (fd >= 0) {
            LibcSocket.close(fd);
            return SocketProbeResult.ok(PosixSocketMode.RAW_PRIVILEGED);
        }
        int err = LibcSocket.errno();
        return SocketProbeResult.fail("SOCK_RAW also failed with errno=" + err);
    }

    // -------------------------------------------------------------------------
    // Hostname resolution
    // -------------------------------------------------------------------------

    private List<MtrHop> resolveHostnames(List<MtrHop> hops) {
        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futs = new ArrayList<>(hops.size());
            for (MtrHop hop : hops) {
                if (hop.getAddress() != null) {
                    final InetAddress addr = hop.getAddress();
                    futs.add(ex.submit(() -> {
                        try {
                            return InetAddress.getByAddress(addr.getAddress()).getHostName();
                        } catch (Exception ignored) { return null; }
                    }));
                } else {
                    futs.add(CompletableFuture.completedFuture(null));
                }
            }
            List<MtrHop> resolved = new ArrayList<>(hops.size());
            for (int i = 0; i < hops.size(); i++) {
                MtrHop hop = hops.get(i);
                String hostname = null;
                try { hostname = futs.get(i).get(2, TimeUnit.SECONDS); }
                catch (Exception ignored) {}
                if (hostname != null && hop.getAddress() != null
                        && hostname.equals(hop.getAddress().getHostAddress())) {
                    hostname = null;
                }
                resolved.add(new MtrHop(
                        hop.getTtl(), hop.getAddress(), hostname,
                        hop.getRtts(), hop.getSent(), hop.getReceived()));
            }
            return resolved;
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /** AF_INET6 value: 10 on Linux, 30 on macOS. */
    private int getAf6() {
        return isMacOs ? 30 : 10;
    }

    private static byte[] buildPayload(int size) {
        byte[] buf = new byte[size];
        for (int i = 0; i < size; i++) buf[i] = (byte) (i & 0xFF);
        return buf;
    }

    private static MtrHop unresponsiveHop(int ttl, int probeCount) {
        return new MtrHop(ttl, null, null, Collections.emptyList(), probeCount, 0);
    }

    private static byte[] segmentToBytes(MemorySegment seg, long len) {
        int n = (int) Math.min(len, seg.byteSize());
        byte[] buf = new byte[n];
        MemorySegment.copy(seg, ValueLayout.JAVA_BYTE, 0, buf, 0, n);
        return buf;
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    private record ProbeResult(InetAddress from, Duration rtt) {}

    private record SocketProbeResult(@Nullable PosixSocketMode mode,
                                     @Nullable String errorReason) {
        static SocketProbeResult ok(PosixSocketMode m)     { return new SocketProbeResult(m, null);     }
        static SocketProbeResult fail(String reason)       { return new SocketProbeResult(null, reason); }
    }
}







