package com.ghostchu.peerbanhelper.util.rule;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface Rule {
    @NotNull
    MatchResult match(@NotNull String content);

    Map<String, Object> metadata();

    default String matcherName() {
        return null;
    }
}
