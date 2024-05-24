package com.ghostchu.peerbanhelper.database;

public record RuleSubLog(
        String ruleId,
        long updateTime,
        int count,
        String updateType
) {
}
