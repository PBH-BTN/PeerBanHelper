package com.ghostchu.peerbanhelper.event.banwave;

import com.ghostchu.peerbanhelper.event.Cancellable;
import com.ghostchu.peerbanhelper.module.FeatureModule;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

public final class FeatureModuleExecuteEvent implements Cancellable {
    private boolean cancelled = false;
    private String cancelReason = null;
    @Getter
    private final FeatureModule featureModule;

    public FeatureModuleExecuteEvent(FeatureModule featureModule) {
        this.featureModule = featureModule;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public @Nullable String getCancelReason() {
        return cancelReason;
    }

    @Override
    public void setCancelled(boolean cancel, @Nullable String reason) {
        this.cancelled = cancel;
        this.cancelReason = reason;
    }
}
