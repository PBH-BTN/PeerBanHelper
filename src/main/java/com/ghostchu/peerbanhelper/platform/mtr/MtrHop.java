package com.ghostchu.peerbanhelper.platform.mtr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Represents the result of a single hop in an MTR traceroute.
 *
 * <p><b>Loss-rate semantics:</b><br>
 * {@code lossRate = (sent - received) / (double) sent}.
 * A hop that never responded (all probes timed out after retries) will have an
 * empty {@code rtts} list and {@code lossRate == 1.0}.
 */
public final class MtrHop {

    /** 1-based TTL for this hop. */
    private final int ttl;

    /**
     * Address of the router that responded, or {@code null} if this hop timed
     * out for all probes (i.e., a {@code * * *} hop).
     */
    @Nullable
    private final InetAddress address;

    /**
     * Reverse-DNS hostname of {@link #address}, populated only when
     * {@link MtrOptions#isResolveHostnames()} is {@code true} and DNS lookup
     * succeeded.  May be {@code null}.
     */
    @Nullable
    private final String hostname;

    /**
     * RTT values for every probe that received a reply.  Empty when the hop is
     * completely unresponsive.  The list is unmodifiable.
     */
    @NotNull
    private final List<Duration> rtts;

    /** Total number of probes sent for this hop (equals {@code probeCount}). */
    private final int sent;

    /** Number of probes that received a reply within the timeout. */
    private final int received;

    /** Fraction of lost probes: {@code (sent - received) / (double) sent}. */
    private final double lossRate;

    public MtrHop(int ttl,
                  @Nullable InetAddress address,
                  @Nullable String hostname,
                  @NotNull List<Duration> rtts,
                  int sent,
                  int received) {
        if (sent < 0) throw new IllegalArgumentException("sent must be >= 0");
        if (received < 0 || received > sent)
            throw new IllegalArgumentException("received must be in [0, sent]");
        this.ttl      = ttl;
        this.address  = address;
        this.hostname = hostname;
        this.rtts     = Collections.unmodifiableList(rtts);
        this.sent     = sent;
        this.received = received;
        this.lossRate = (sent == 0) ? 0.0 : (double) (sent - received) / sent;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int getTtl()                         { return ttl;      }
    public @Nullable InetAddress getAddress()   { return address;  }
    public @Nullable String getHostname()       { return hostname; }
    public @NotNull List<Duration> getRtts()    { return rtts;     }
    public int getSent()                        { return sent;     }
    public int getReceived()                    { return received; }
    public double getLossRate()                 { return lossRate; }

    /**
     * Returns the minimum RTT across successful probes, or {@link Duration#ZERO}
     * if no reply was received.
     */
    @NotNull
    public Duration getMinRtt() {
        return rtts.stream().min(Duration::compareTo).orElse(Duration.ZERO);
    }

    /**
     * Returns the maximum RTT across successful probes, or {@link Duration#ZERO}
     * if no reply was received.
     */
    @NotNull
    public Duration getMaxRtt() {
        return rtts.stream().max(Duration::compareTo).orElse(Duration.ZERO);
    }

    /**
     * Returns the arithmetic mean RTT across successful probes, or
     * {@link Duration#ZERO} if no reply was received.
     */
    @NotNull
    public Duration getAvgRtt() {
        if (rtts.isEmpty()) return Duration.ZERO;
        long totalNanos = rtts.stream().mapToLong(Duration::toNanos).sum();
        return Duration.ofNanos(totalNanos / rtts.size());
    }

    /** {@code true} if this hop never responded (all probes lost). */
    public boolean isUnresponsive() {
        return address == null;
    }

    /**
     * Best display name: hostname if available, otherwise IP string, or
     * {@code "*"} for unresponsive hops.
     */
    @NotNull
    public String getDisplayName() {
        if (address == null) return "*";
        if (hostname != null && !hostname.isBlank()) return hostname;
        return address.getHostAddress();
    }

    @Override
    public String toString() {
        return String.format("MtrHop{ttl=%d, addr=%s, sent=%d, recv=%d, loss=%.1f%%, avgRtt=%s}",
                ttl,
                getDisplayName(),
                sent,
                received,
                lossRate * 100,
                getAvgRtt());
    }
}

