package com.ghostchu.peerbanhelper.database;

import org.jetbrains.annotations.NotNull;

public record RuleSubInfo(
        @NotNull String ruleId,
        boolean enabled,
        @NotNull String ruleName,
        @NotNull String subUrl,
        long lastUpdate,
        int entCount
) {
}
