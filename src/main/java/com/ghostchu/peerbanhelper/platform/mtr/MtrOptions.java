package com.ghostchu.peerbanhelper.platform.mtr;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Configuration options for a single MTR (My Traceroute) trace operation.
 *
 * <p>Use {@link Builder} to construct instances with explicit or default values.
 *
 * <h3>Probe / retry semantics</h3>
 * <ul>
 *   <li>{@code probeCount} – number of independent probe rounds sent per hop
 *       (determines denominator for loss-rate calculation).</li>
 *   <li>{@code retryCount} – extra retry attempts when a single probe receives
 *       no reply within {@code probeTimeout}. Only a probe that is still
 *       unanswered after all retries counts as <em>lost</em>.
 *       Maximum packets sent per hop = {@code probeCount × (1 + retryCount)}.</li>
 * </ul>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * MtrOptions opts = MtrOptions.builder()
 *         .maxHops(30)
 *         .probeCount(3)
 *         .retryCount(2)
 *         .probeTimeout(Duration.ofSeconds(1))
 *         .totalTimeout(Duration.ofSeconds(60))
 *         .payloadSize(56)
 *         .resolveHostnames(false)
 *         .dscp(0)
 *         .build();
 * }</pre>
 */
public final class MtrOptions {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Maximum TTL / hop count before stopping the trace. Default: {@code 30}. */
    private final int maxHops;

    /**
     * Number of probe packets sent per hop (used as denominator for loss-rate).
     * Default: {@code 3}.
     */
    private final int probeCount;

    /**
     * Number of extra retry attempts when a probe receives no reply within
     * {@link #probeTimeout}. Default: {@code 2}.
     *
     * <p>Setting this to {@code 0} disables retries; a non-responding hop is
     * immediately counted as lost after one timeout.
     */
    private final int retryCount;

    /**
     * Per-probe timeout (including all retry rounds).  The effective wait per
     * probe attempt is {@code probeTimeout / (1 + retryCount)}.
     * Default: {@code 1 s}.
     */
    private final Duration probeTimeout;

    /**
     * Hard upper bound on the total time the trace may run.  When exceeded,
     * {@link com.ghostchu.peerbanhelper.platform.mtr.exception.MtrTimeoutException}
     * is thrown.  Default: {@code 60 s}.
     */
    private final Duration totalTimeout;

    /**
     * ICMP payload size in bytes (excluding IP and ICMP headers).
     * Default: {@code 56} bytes (same as the classic {@code ping} default).
     */
    private final int payloadSize;

    /**
     * When {@code true}, each responding hop address is resolved to a hostname
     * via reverse-DNS (PTR) lookup.  Results are stored in
     * {@link MtrHop#getHostname()}.  Default: {@code false}.
     */
    private final boolean resolveHostnames;

    /**
     * IP DSCP value (6 bits, 0–63) placed in the TOS / Traffic Class byte.
     * Default: {@code 0} (best-effort).
     */
    private final int dscp;

    // -------------------------------------------------------------------------
    // Constructor (private — use Builder)
    // -------------------------------------------------------------------------

    private MtrOptions(Builder b) {
        this.maxHops          = b.maxHops;
        this.probeCount       = b.probeCount;
        this.retryCount       = b.retryCount;
        this.probeTimeout     = b.probeTimeout;
        this.totalTimeout     = b.totalTimeout;
        this.payloadSize      = b.payloadSize;
        this.resolveHostnames = b.resolveHostnames;
        this.dscp             = b.dscp;
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /** Returns a {@link Builder} pre-filled with recommended defaults. */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /** Returns a {@link MtrOptions} instance with all defaults applied. */
    @NotNull
    public static MtrOptions defaults() {
        return builder().build();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int getMaxHops()              { return maxHops;          }
    public int getProbeCount()           { return probeCount;       }
    public int getRetryCount()           { return retryCount;       }
    public @NotNull Duration getProbeTimeout()  { return probeTimeout;    }
    public @NotNull Duration getTotalTimeout()  { return totalTimeout;    }
    public int getPayloadSize()          { return payloadSize;      }
    public boolean isResolveHostnames()  { return resolveHostnames; }
    public int getDscp()                 { return dscp;             }

    /**
     * Converts {@link #dscp} to the 8-bit TOS / Traffic Class byte
     * ({@code dscp << 2}).
     */
    public int tosToByte() {
        return (dscp & 0x3F) << 2;
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private void validate() {
        if (maxHops < 1 || maxHops > 255)
            throw new IllegalArgumentException("maxHops must be in [1, 255], got " + maxHops);
        if (probeCount < 1)
            throw new IllegalArgumentException("probeCount must be >= 1, got " + probeCount);
        if (retryCount < 0)
            throw new IllegalArgumentException("retryCount must be >= 0, got " + retryCount);
        if (probeTimeout == null || probeTimeout.isNegative() || probeTimeout.isZero())
            throw new IllegalArgumentException("probeTimeout must be positive");
        if (totalTimeout == null || totalTimeout.isNegative() || totalTimeout.isZero())
            throw new IllegalArgumentException("totalTimeout must be positive");
        if (payloadSize < 0 || payloadSize > 65507)
            throw new IllegalArgumentException("payloadSize must be in [0, 65507], got " + payloadSize);
        if (dscp < 0 || dscp > 63)
            throw new IllegalArgumentException("dscp must be in [0, 63], got " + dscp);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder {

        private int      maxHops          = 30;
        private int      probeCount       = 3;
        private int      retryCount       = 2;
        private Duration probeTimeout     = Duration.ofSeconds(1);
        private Duration totalTimeout     = Duration.ofSeconds(60);
        private int      payloadSize      = 56;
        private boolean  resolveHostnames = false;
        private int      dscp             = 0;

        private Builder() {}

        /**
         * Maximum TTL / hop count (1–255).  Default: {@code 30}.
         */
        public Builder maxHops(int maxHops) {
            this.maxHops = maxHops;
            return this;
        }

        /**
         * Number of probes sent per hop.  Determines the loss-rate denominator.
         * Default: {@code 3}.
         */
        public Builder probeCount(int probeCount) {
            this.probeCount = probeCount;
            return this;
        }

        /**
         * Extra retries per probe attempt on no-reply.  {@code 0} = no retry.
         * Default: {@code 2}.
         */
        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        /**
         * Timeout applied to each individual probe send+receive cycle.
         * Default: {@code 1 s}.
         */
        public Builder probeTimeout(Duration probeTimeout) {
            this.probeTimeout = probeTimeout;
            return this;
        }

        /**
         * Hard total-trace timeout.  Default: {@code 60 s}.
         */
        public Builder totalTimeout(Duration totalTimeout) {
            this.totalTimeout = totalTimeout;
            return this;
        }

        /**
         * ICMP echo payload size in bytes (0–65507).  Default: {@code 56}.
         */
        public Builder payloadSize(int payloadSize) {
            this.payloadSize = payloadSize;
            return this;
        }

        /**
         * Enable reverse-DNS hostname resolution per hop.  Default: {@code false}.
         */
        public Builder resolveHostnames(boolean resolveHostnames) {
            this.resolveHostnames = resolveHostnames;
            return this;
        }

        /**
         * IP DSCP field value (0–63).  Default: {@code 0} (best-effort).
         */
        public Builder dscp(int dscp) {
            this.dscp = dscp;
            return this;
        }

        /** Validates and builds the {@link MtrOptions} instance. */
        @NotNull
        public MtrOptions build() {
            MtrOptions opts = new MtrOptions(this);
            opts.validate();
            return opts;
        }
    }

    @Override
    public String toString() {
        return "MtrOptions{" +
               "maxHops=" + maxHops +
               ", probeCount=" + probeCount +
               ", retryCount=" + retryCount +
               ", probeTimeout=" + probeTimeout +
               ", totalTimeout=" + totalTimeout +
               ", payloadSize=" + payloadSize +
               ", resolveHostnames=" + resolveHostnames +
               ", dscp=" + dscp +
               '}';
    }
}


