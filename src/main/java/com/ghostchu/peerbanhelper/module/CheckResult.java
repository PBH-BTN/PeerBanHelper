package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import org.jetbrains.annotations.NotNull;

public record CheckResult(
        @NotNull Class<?> moduleContext,
        @NotNull PeerAction action,
        long duration,
        @NotNull TranslationComponent rule,
        @NotNull TranslationComponent reason
) {
}
