package com.ghostchu.peerbanhelper.banpipeline;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import org.jetbrains.annotations.Nullable;

public record BanOrganCallback<IN>(
        IN input,
        BanOrganCallbackResult result,
        Throwable error,
        @Nullable
        TranslationComponent message
) {
}
