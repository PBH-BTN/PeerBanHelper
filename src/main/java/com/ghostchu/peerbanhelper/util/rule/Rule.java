package com.ghostchu.peerbanhelper.util.rule;

import org.jetbrains.annotations.NotNull;

public interface Rule {
    @NotNull
    MatchResult match(@NotNull String content);
}
