package com.ghostchu.peerbanhelper.banpipeline;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import org.jetbrains.annotations.Nullable;

public record BanOrganCallback (
     BanOrganCallbackResult result,
     Throwable error,
     @Nullable
     TranslationComponent message
){}
