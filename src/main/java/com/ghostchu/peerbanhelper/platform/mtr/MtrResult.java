package com.ghostchu.peerbanhelper.platform.mtr;

import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * The complete result of an MTR (My Traceroute) operation.
 */
public final class MtrResult {

    /** The destination address that was traced. */
    @NotNull
    private final InetAddress target;

    /**
     * Ordered list of hops, index 0 = TTL 1 (first router after the local
     * host).  The list is unmodifiable.
     */
    @NotNull
    private final List<MtrHop> hops;

    /** Wall-clock time when the trace was initiated. */
    @NotNull
    private final Instant startTime;

    /** Total elapsed time from trace start to completion. */
    @NotNull
    private final Duration totalElapsed;

    public MtrResult(@NotNull InetAddress target,
                     @NotNull List<MtrHop> hops,
                     @NotNull Instant startTime,
                     @NotNull Duration totalElapsed) {
        this.target       = target;
        this.hops         = Collections.unmodifiableList(hops);
        this.startTime    = startTime;
        this.totalElapsed = totalElapsed;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public @NotNull InetAddress getTarget()        { return target;       }
    public @NotNull List<MtrHop> getHops()         { return hops;         }
    public @NotNull Instant getStartTime()         { return startTime;    }
    public @NotNull Duration getTotalElapsed()     { return totalElapsed; }

    /** Number of hops in the trace result. */
    public int getHopCount() { return hops.size(); }

    /**
     * Overall packet-loss rate averaged across all hops that had at least one
     * sent probe.
     */
    public double getOverallLossRate() {
        if (hops.isEmpty()) return 0.0;
        return hops.stream()
                   .filter(h -> h.getSent() > 0)
                   .mapToDouble(MtrHop::getLossRate)
                   .average()
                   .orElse(0.0);
    }

    /**
     * Whether the trace actually reached the target (i.e., the last hop that
     * responded has the same address as {@link #target}).
     */
    public boolean reachedTarget() {
        for (int i = hops.size() - 1; i >= 0; i--) {
            InetAddress addr = hops.get(i).getAddress();
            if (addr != null) {
                return addr.equals(target);
            }
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("MtrResult{target=%s, hops=%d, elapsed=%s, reachedTarget=%b}%n",
                target.getHostAddress(), hops.size(), totalElapsed, reachedTarget()));
        for (MtrHop hop : hops) {
            sb.append("  ").append(hop).append(System.lineSeparator());
        }
        return sb.toString();
    }
}

