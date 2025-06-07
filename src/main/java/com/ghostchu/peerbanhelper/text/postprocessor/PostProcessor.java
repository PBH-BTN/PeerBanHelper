package com.ghostchu.peerbanhelper.text.postprocessor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Post-processing
 */
public interface PostProcessor {
    @NotNull
    String process(@NotNull String text, @Nullable String locale, @Nullable String... args);
}
