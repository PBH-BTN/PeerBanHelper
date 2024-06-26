package com.ghostchu.peerbanhelper.database;

import com.ghostchu.peerbanhelper.module.IPBanRuleUpdateType;
import org.jetbrains.annotations.NotNull;

public record RuleSubLog(
        @NotNull String ruleId,
        long updateTime,
        int count,
        @NotNull IPBanRuleUpdateType updateType
) {
}
