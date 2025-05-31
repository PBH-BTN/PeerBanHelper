package com.ghostchu.peerbanhelper.api.module;

import com.ghostchu.peerbanhelper.api.text.TranslationComponent;
import org.jetbrains.annotations.NotNull;

public record CheckResult(
        @NotNull Class<?> moduleContext,
        @NotNull PeerAction action,
        long duration,
        @NotNull TranslationComponent rule,
        @NotNull TranslationComponent reason
) {
}
