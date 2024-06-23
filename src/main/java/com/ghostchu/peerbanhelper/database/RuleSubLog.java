package com.ghostchu.peerbanhelper.database;

import com.ghostchu.peerbanhelper.module.IPBanRuleUpdateType;

public record RuleSubLog(
        String ruleId,
        long updateTime,
        int count,
        IPBanRuleUpdateType updateType
) {
}
