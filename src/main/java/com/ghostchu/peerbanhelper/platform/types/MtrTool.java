package com.ghostchu.peerbanhelper.platform.types;

import com.ghostchu.peerbanhelper.platform.mtr.MtrOptions;
import com.ghostchu.peerbanhelper.platform.mtr.MtrResult;
import com.ghostchu.peerbanhelper.platform.mtr.exception.MtrException;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;

/**
 * Platform-aware MTR (My Traceroute) tool using ICMP probes.
 *
 * <p>Implementations are required to:
 * <ul>
 *   <li>Support parallel TTL probing for performance.</li>
 *   <li>Honour {@link MtrOptions#getRetryCount()} to reduce false packet-loss.</li>
 *   <li>Throw the appropriate {@link MtrException} subclass on errors.</li>
 * </ul>
 */
public interface MtrTool {

    /**
     * Runs a full MTR trace to {@code target}.
     *
     * @param target  destination address (IPv4 or IPv6)
     * @param options tuning parameters
     * @return the complete trace result
     * @throws MtrException (or any sub-class) on any failure
     */
    @NotNull
    MtrResult trace(@NotNull InetAddress target, @NotNull MtrOptions options) throws MtrException;

    /**
     * Returns {@code true} if this implementation can trace the given address
     * on the current platform without throwing an unsupported exception.
     * <p>
     * Callers should check this before invoking {@link #trace} to provide a
     * meaningful error message to the user.
     *
     * @param target the address to check
     * @return {@code true} if tracing is expected to work
     */
    boolean isSupported(@NotNull InetAddress target);
}

