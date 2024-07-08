package com.ghostchu.peerbanhelper.module;

import org.jetbrains.annotations.NotNull;

public record CheckResult(
        @NotNull Class<?> moduleContext,
        @NotNull PeerAction action,
        long duration,
        @NotNull String rule,
        @NotNull String reason
) {
}
