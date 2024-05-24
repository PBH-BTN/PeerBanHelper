package com.ghostchu.peerbanhelper.database;

public record RuleSubInfo(
        String ruleId,
        boolean enabled,
        String ruleName,
        String subUrl,
        long lastUpdate,
        int entCount
) {
}
