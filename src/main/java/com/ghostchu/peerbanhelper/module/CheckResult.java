package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import org.jetbrains.annotations.NotNull;

public record CheckResult(
        @NotNull Class<?> moduleContext,
        @NotNull PeerAction action,
        long duration,
        Long throttledUploadRate,
        Long throttledDownloadRate,
        @NotNull TranslationComponent rule,
        @NotNull TranslationComponent reason
) {
}
