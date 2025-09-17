package com.ghostchu.peerbanhelper.event;

import org.jetbrains.annotations.Nullable;

public interface Cancellable {
    boolean isCancelled();
    @Nullable String getCancelReason();
    void setCancelled(boolean cancel, @Nullable String reason);
}
