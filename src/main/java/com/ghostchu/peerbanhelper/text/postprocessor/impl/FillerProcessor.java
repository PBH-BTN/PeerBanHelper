package com.ghostchu.peerbanhelper.text.postprocessor.impl;

import com.ghostchu.peerbanhelper.text.postprocessor.PostProcessor;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@EqualsAndHashCode
public final class FillerProcessor implements PostProcessor {
    @Override
    public @NotNull String process(@NotNull String text, @Nullable String locale, @Nullable String... args) {
        return MsgUtil.fillArgs(text, args);
    }
}
