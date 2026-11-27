package com.ghostchu.peerbanhelper.module;

import org.jetbrains.annotations.Nullable;

/**
 * Execution context for peer rule checks.
 * <p>
 * Implementations may expose progress information, but rule modules must not rely on a
 * scheduler-specific pipeline object.
 */
public interface PeerCheckContext {
    PeerCheckContext NOOP = (io, comment) -> {
    };

    void setComment(boolean io, @Nullable String comment);
}
