package com.ghostchu.peerbanhelper.platform.impl.win32.mtr;

import com.ghostchu.peerbanhelper.platform.mtr.MtrHop;
import com.ghostchu.peerbanhelper.platform.mtr.MtrOptions;
import com.ghostchu.peerbanhelper.platform.mtr.MtrResult;
import com.ghostchu.peerbanhelper.platform.mtr.exception.MtrException;
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

/**
 * Windows implementation of {@link MtrTool} using the high-level
 * {@code iphlpapi.dll} ICMP API.
 *
 * <p>Key properties:
 * <ul>
 *   <li>Works without administrator / elevated privileges.</li>
 *   <li>All TTL levels are probed <em>in parallel</em> using virtual threads for
 *       maximum performance.</li>
 *   <li>Each probe is retried up to {@link MtrOptions#getRetryCount()} times on
 *       timeout before being counted as a loss.</li>
 *   <li>Supports both IPv4 ({@code IcmpSendEcho2Ex}) and IPv6
 *       ({@code Icmp6SendEcho2}).</li>
 * </ul>
 */
@Slf4j
public final class WindowsMtrTool implements MtrTool {

    /** Shared IPv4 ICMP handle (opened once, kept for the life of the object). */
    private final MemorySegment icmpHandle4;

    /** Shared IPv6 ICMP handle – may be {@code null} if Icmp6CreateFile failed. */
    @Nullable
    private final MemorySegment icmpHandle6;

    /** Whether the IPv6 handle was successfully opened. */
    private final boolean ipv6Available;

    // -------------------------------------------------------------------------
    // Construction / teardown
    // -------------------------------------------------------------------------

    public WindowsMtrTool() {
        // IPv4 handle – must succeed
        MemorySegment h4 = IcmpLib.icmpCreateFile();
        if (IcmpLib.isInvalidHandle(h4)) {
            throw new RuntimeException("IcmpCreateFile returned INVALID_HANDLE_VALUE");
        }
        this.icmpHandle4 = h4;

        // IPv6 handle – best-effort
        MemorySegment h6 = null;
        boolean v6ok = false;
        try {
            h6 = IcmpLib.icmp6CreateFile();
            v6ok = !IcmpLib.isInvalidHandle(h6);
            if (!v6ok) {
                log.debug("Icmp6CreateFile returned INVALID_HANDLE_VALUE; IPv6 MTR not available");
                h6 = null;
            }
        } catch (Exception e) {
            log.debug("Icmp6CreateFile threw exception; IPv6 MTR not available: {}", e.getMessage());
        }
        this.icmpHandle6 = h6;
        this.ipv6Available = v6ok;
    }

    public void close() {
        try { IcmpLib.icmpCloseHandle(icmpHandle4); } catch (Exception ignored) {}
        if (icmpHandle6 != null) {
            try { IcmpLib.icmpCloseHandle(icmpHandle6); } catch (Exception ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // MtrTool interface
    // -------------------------------------------------------------------------

    @Override
    public boolean isSupported(@NotNull InetAddress target) {
        if (target instanceof Inet4Address) return true;
        if (target instanceof Inet6Address) return ipv6Available;
        return false;
    }

    @Override
    public @NotNull MtrResult trace(@NotNull InetAddress target, @NotNull MtrOptions options)
            throws MtrException {
        if (!isSupported(target)) {
            throw new MtrUnsupportedException(
                    "IPv6 ICMP tracing is not available on this system (Icmp6CreateFile failed)");
        }

        Instant start = Instant.now();

        // Build a random payload once; reuse across probes for efficiency
        byte[] payload = buildPayload(options.getPayloadSize());

        // Launch all TTL probes in parallel using virtual threads
        List<Future<MtrHop>> futures = new ArrayList<>(options.getMaxHops());
        long totalDeadlineMs = options.getTotalTimeout().toMillis();
        List<MtrHop> hops = new ArrayList<>(options.getMaxHops());

        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int ttl = 1; ttl <= options.getMaxHops(); ttl++) {
                final int currentTtl = ttl;
                futures.add(exec.submit(() -> probeHop(target, currentTtl, payload, options)));
            }
            exec.shutdown();
            try {
                if (!exec.awaitTermination(totalDeadlineMs, TimeUnit.MILLISECONDS)) {
                    exec.shutdownNow();
                    throw new MtrTimeoutException(
                            "Total trace timeout exceeded (" + options.getTotalTimeout() + ")");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MtrException("Trace interrupted", e);
            }
        }

        // Collect results in TTL order
        for (int ttl = 1; ttl <= options.getMaxHops(); ttl++) {
            Future<MtrHop> f = futures.get(ttl - 1);
            MtrHop hop;
            try {
                hop = f.get();
            } catch (ExecutionException e) {
                log.debug("TTL={} probe threw: {}", ttl, e.getCause().getMessage());
                hop = unresponsiveHop(ttl, options.getProbeCount());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MtrException("Trace interrupted while collecting hop " + ttl, e);
            } catch (CancellationException e) {
                hop = unresponsiveHop(ttl, options.getProbeCount());
            }
            hops.add(hop);

            if (hop.getAddress() != null && hop.getAddress().equals(target)) {
                break; // reached destination – stop collecting
            }
        }

        // Optionally resolve PTR for each hop (batch, after probing)
        if (options.isResolveHostnames()) {
            hops = resolveHostnames(hops);
        }

        Duration elapsed = Duration.between(start, Instant.now());
        return new MtrResult(target, hops, start, elapsed);
    }

    // -------------------------------------------------------------------------
    // Per-hop probing
    // -------------------------------------------------------------------------

    /**
     * Sends {@code probeCount} probes at the given TTL.  Each probe is retried
     * up to {@code retryCount} times on timeout.
     */
    private MtrHop probeHop(InetAddress target, int ttl, byte[] payload, MtrOptions options)
            throws MtrException {
        List<Duration> rtts = new ArrayList<>(options.getProbeCount());
        int sent     = 0;
        int received = 0;
        InetAddress replyAddr = null;

        for (int probe = 0; probe < options.getProbeCount(); probe++) {
            sent++;
            Duration rtt = null;

            for (int attempt = 0; attempt <= options.getRetryCount(); attempt++) {
                long timeoutMs = computeAttemptTimeoutMs(options);
                ProbeResult result = sendSingleProbe(target, ttl, payload, (int) timeoutMs, options);

                if (result != null) {
                    // Got a reply (TTL-expired or echo-reply)
                    rtt = result.rtt;
                    if (replyAddr == null) replyAddr = result.from;
                    break; // no more retries needed
                }
                // else: timed out – retry if attempts remain
            }

            if (rtt != null) {
                rtts.add(rtt);
                received++;
            }
        }

        return new MtrHop(ttl, replyAddr, null, rtts, sent, received);
    }

    /**
     * Per-attempt timeout: divide the probe budget evenly across (1 + retryCount)
     * attempts so we still honour probeTimeout overall.
     */
    private long computeAttemptTimeoutMs(MtrOptions options) {
        long budget = options.getProbeTimeout().toMillis();
        // Simple: each attempt gets an equal share
        return Math.max(100L, budget / (options.getRetryCount() + 1));
    }

    /**
     * Sends a single ICMP echo request and returns the result or {@code null} on
     * timeout/no-reply.
     */
    @Nullable
    private ProbeResult sendSingleProbe(InetAddress target, int ttl,
                                        byte[] payload, int timeoutMs,
                                        MtrOptions options) throws MtrException {
        if (target instanceof Inet4Address) {
            return sendProbeV4((Inet4Address) target, ttl, payload, timeoutMs, options);
        } else {
            return sendProbeV6((Inet6Address) target, ttl, payload, timeoutMs, options);
        }
    }

    // -------------------------------------------------------------------------
    // IPv4 probe
    // -------------------------------------------------------------------------

    @Nullable
    private ProbeResult sendProbeV4(Inet4Address target, int ttl,
                                    byte[] payload, int timeoutMs,
                                    MtrOptions options) throws MtrException {
        try (Arena arena = Arena.ofConfined()) {
            // Build IP_OPTION_INFORMATION
            MemorySegment opts = arena.allocate(IcmpEchoReplyLayout.IP_OPTION_INFORMATION);
            opts.set(ValueLayout.JAVA_BYTE,
                     IcmpEchoReplyLayout.IP_OPT_OFFSET_TTL,
                     (byte) ttl);
            opts.set(ValueLayout.JAVA_BYTE,
                     IcmpEchoReplyLayout.IP_OPT_OFFSET_TOS,
                     (byte) options.tosToByte());

            // Request payload
            MemorySegment reqData = arena.allocate(payload.length == 0 ? 1 : payload.length);
            MemorySegment.copy(payload, 0, reqData, ValueLayout.JAVA_BYTE, 0, payload.length);

            // Reply buffer
            int replyBufSize = IcmpEchoReplyLayout.IPV4_REPLY_BUFFER_SIZE
                               + Math.max(0, payload.length - 56); // extra room for payload echo
            MemorySegment replyBuf = arena.allocate(replyBufSize);

            // Convert destination address to DWORD (big-endian int)
            int dstAddrInt = bytesToInt(target.getAddress());

            long tBefore = System.nanoTime();
            int replies = IcmpLib.icmpSendEcho2Ex(
                    icmpHandle4,
                    0, dstAddrInt,          // src=any, dst
                    reqData, (short) payload.length,
                    opts,
                    replyBuf, replyBufSize,
                    timeoutMs);
            long tAfter = System.nanoTime();

            if (replies == 0) {
                return null; // timeout or no reply
            }

            // Parse ICMP_ECHO_REPLY
            int status = replyBuf.get(ValueLayout.JAVA_INT,
                                      IcmpEchoReplyLayout.ECHO_REPLY_OFFSET_STATUS);

            if (status == IcmpLib.IP_REQ_TIMED_OUT) {
                return null;
            }

            // For MTR we accept both success (destination reached) and
            // TTL-expired (intermediate hop).
            if (status != IcmpLib.IP_SUCCESS
                    && status != IcmpLib.IP_TTL_EXPIRED_TRANSIT
                    && status != IcmpLib.IP_TTL_EXPIRED_REASSEM
                    && status != IcmpLib.IP_DEST_NET_UNREACHABLE
                    && status != IcmpLib.IP_DEST_HOST_UNREACHABLE
                    && status != IcmpLib.IP_DEST_PORT_UNREACHABLE) {
                log.trace("IcmpSendEcho2Ex status {} for TTL={}", status, ttl);
                return null;
            }

            // Extract reply address
            int addrInt = replyBuf.get(ValueLayout.JAVA_INT,
                                       IcmpEchoReplyLayout.ECHO_REPLY_OFFSET_ADDRESS);
            InetAddress from = InetAddress.getByAddress(intToBytes(addrInt));

            // Prefer Windows-reported RTT but fall back to wall-clock
            int rttMs = replyBuf.get(ValueLayout.JAVA_INT,
                                     IcmpEchoReplyLayout.ECHO_REPLY_OFFSET_RTT);
            Duration rtt = rttMs > 0
                    ? Duration.ofMillis(rttMs)
                    : Duration.ofNanos(tAfter - tBefore);

            return new ProbeResult(from, rtt);
        } catch (UnknownHostException e) {
            throw new MtrException("Failed to parse reply address", e);
        }
    }

    // -------------------------------------------------------------------------
    // IPv6 probe
    // -------------------------------------------------------------------------

    @Nullable
    private ProbeResult sendProbeV6(Inet6Address target, int ttl,
                                    byte[] payload, int timeoutMs,
                                    MtrOptions options) throws MtrException {
        if (icmpHandle6 == null) {
            throw new MtrUnsupportedException("IPv6 ICMP handle not available");
        }
        try (Arena arena = Arena.ofConfined()) {
            // Build IP_OPTION_INFORMATION (TTL = hop limit; TOS = DSCP for IPv6)
            MemorySegment opts = arena.allocate(IcmpEchoReplyLayout.IP_OPTION_INFORMATION);
            opts.set(ValueLayout.JAVA_BYTE,
                     IcmpEchoReplyLayout.IP_OPT_OFFSET_TTL,
                     (byte) ttl);
            opts.set(ValueLayout.JAVA_BYTE,
                     IcmpEchoReplyLayout.IP_OPT_OFFSET_TOS,
                     (byte) options.tosToByte());

            // Request payload
            MemorySegment reqData = arena.allocate(Math.max(1, payload.length));
            if (payload.length > 0) {
                MemorySegment.copy(payload, 0, reqData, ValueLayout.JAVA_BYTE, 0, payload.length);
            }

            // Build sockaddr_in6 for destination
            MemorySegment dstSockAddr = buildSockAddrIn6(arena, target.getAddress());
            // Source: all-zeros (any interface)
            MemorySegment srcSockAddr = arena.allocate(IcmpEchoReplyLayout.SOCKADDR_IN6_SIZE);
            srcSockAddr.set(ValueLayout.JAVA_SHORT,
                            IcmpEchoReplyLayout.SA6_OFFSET_FAMILY,
                            IcmpEchoReplyLayout.AF_INET6);

            // Reply buffer
            int replyBufSize = IcmpEchoReplyLayout.IPV6_REPLY_BUFFER_SIZE;
            MemorySegment replyBuf = arena.allocate(replyBufSize);

            long tBefore = System.nanoTime();
            int replies = IcmpLib.icmp6SendEcho2(
                    icmpHandle6,
                    srcSockAddr, dstSockAddr,
                    reqData, (short) payload.length,
                    opts,
                    replyBuf, replyBufSize,
                    timeoutMs);
            long tAfter = System.nanoTime();

            if (replies == 0) return null;

            // Parse ICMPV6_ECHO_REPLY
            int status = (int) Integer.toUnsignedLong(
                    replyBuf.get(ValueLayout.JAVA_INT, IcmpEchoReplyLayout.ECHO6_STATUS_OFFSET));

            if (status == IcmpLib.IP_REQ_TIMED_OUT) return null;
            if (status != IcmpLib.IP_SUCCESS
                    && status != IcmpLib.IP_TTL_EXPIRED_TRANSIT
                    && status != IcmpLib.IP_TTL_EXPIRED_REASSEM) {
                log.trace("Icmp6SendEcho2 status {} for TTL={}", status, ttl);
                return null;
            }

            // Extract 16-byte IPv6 address
            byte[] addrBytes = new byte[16];
            MemorySegment.copy(replyBuf, ValueLayout.JAVA_BYTE,
                               IcmpEchoReplyLayout.ECHO6_ADDR_OFFSET,
                               addrBytes, 0, 16);
            InetAddress from = InetAddress.getByAddress(addrBytes);

            int rttMs = replyBuf.get(ValueLayout.JAVA_INT, IcmpEchoReplyLayout.ECHO6_RTT_OFFSET);
            Duration rtt = rttMs > 0
                    ? Duration.ofMillis(rttMs)
                    : Duration.ofNanos(tAfter - tBefore);

            return new ProbeResult(from, rtt);
        } catch (UnknownHostException e) {
            throw new MtrException("Failed to parse IPv6 reply address", e);
        }
    }

    // -------------------------------------------------------------------------
    // Hostname resolution (post-trace, uses standard Java DNS)
    // -------------------------------------------------------------------------

    private List<MtrHop> resolveHostnames(List<MtrHop> hops) {
        // Use virtual threads to parallelize PTR lookups
        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = new ArrayList<>(hops.size());
            for (MtrHop hop : hops) {
                if (hop.getAddress() != null) {
                    final InetAddress addr = hop.getAddress();
                    futures.add(ex.submit(() -> {
                        try {
                            // getHostName() triggers a PTR lookup
                            return InetAddress.getByAddress(addr.getAddress()).getHostName();
                        } catch (Exception ignored) {
                            return null;
                        }
                    }));
                } else {
                    futures.add(CompletableFuture.completedFuture(null));
                }
            }

            List<MtrHop> resolved = new ArrayList<>(hops.size());
            for (int i = 0; i < hops.size(); i++) {
                MtrHop hop = hops.get(i);
                String hostname = null;
                try {
                    hostname = futures.get(i).get(2, TimeUnit.SECONDS);
                } catch (Exception ignored) {}

                // If hostname == IP string, don't bother storing it
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
    // Helpers
    // -------------------------------------------------------------------------

    private static byte[] buildPayload(int size) {
        byte[] buf = new byte[size];
        // Fill with a recognisable pattern
        for (int i = 0; i < size; i++) buf[i] = (byte) (i & 0xFF);
        return buf;
    }

    private static MtrHop unresponsiveHop(int ttl, int probeCount) {
        return new MtrHop(ttl, null, null, Collections.emptyList(), probeCount, 0);
    }

    /**
     * Converts a 4-byte network-order (big-endian) address array to a
     * Windows {@code IPAddr} DWORD (little-endian / host byte order on x86).
     * e.g. 192.168.0.1 → bytes [192,168,0,1] → int 0x0100A8C0 (little-endian)
     */
    private static int bytesToInt(byte[] addr) {
        // Windows IPAddr is stored in host (little-endian) byte order.
        // addr[0] is the most-significant octet in dotted-decimal notation,
        // which must become the least-significant byte of the DWORD.
        return  (addr[0] & 0xFF)
             | ((addr[1] & 0xFF) <<  8)
             | ((addr[2] & 0xFF) << 16)
             | ((addr[3] & 0xFF) << 24);
    }

    /**
     * Converts a Windows {@code IPAddr} DWORD (little-endian) back to a
     * 4-byte network-order (big-endian) array for {@link InetAddress#getByAddress}.
     * e.g. int 0x0100A8C0 → bytes [192,168,0,1]
     */
    private static byte[] intToBytes(int addr) {
        return new byte[]{
                (byte)  addr,           // least-significant byte → first octet
                (byte) (addr >>>  8),
                (byte) (addr >>> 16),
                (byte) (addr >>> 24)    // most-significant byte  → last octet
        };
    }

    /**
     * Allocates and fills a {@code sockaddr_in6} structure in the given arena.
     *
     * @param arena    the arena to allocate from
     * @param addrBytes 16-byte IPv6 address (network byte order)
     */
    private static MemorySegment buildSockAddrIn6(Arena arena, byte[] addrBytes) {
        MemorySegment seg = arena.allocate(IcmpEchoReplyLayout.SOCKADDR_IN6_SIZE);
        seg.set(ValueLayout.JAVA_SHORT,
                IcmpEchoReplyLayout.SA6_OFFSET_FAMILY,
                IcmpEchoReplyLayout.AF_INET6);
        seg.set(ValueLayout.JAVA_SHORT, IcmpEchoReplyLayout.SA6_OFFSET_PORT, (short) 0);
        seg.set(ValueLayout.JAVA_INT,   IcmpEchoReplyLayout.SA6_OFFSET_FLOWINFO, 0);
        MemorySegment.copy(addrBytes, 0, seg, ValueLayout.JAVA_BYTE,
                           IcmpEchoReplyLayout.SA6_OFFSET_ADDR, 16);
        seg.set(ValueLayout.JAVA_INT, IcmpEchoReplyLayout.SA6_OFFSET_SCOPE_ID, 0);
        return seg;
    }

    // -------------------------------------------------------------------------
    // Inner result carrier
    // -------------------------------------------------------------------------

    private record ProbeResult(InetAddress from, Duration rtt) {}
}





