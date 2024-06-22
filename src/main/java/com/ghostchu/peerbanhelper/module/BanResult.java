package com.ghostchu.peerbanhelper.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record BanResult(@Nullable FeatureModule moduleContext, @NotNull PeerAction action, @NotNull String rule,
                        @NotNull String reason) {
}
