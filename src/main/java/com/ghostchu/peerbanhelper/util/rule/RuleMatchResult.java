package com.ghostchu.peerbanhelper.util.rule;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import org.jetbrains.annotations.Nullable;

public record RuleMatchResult(MatchResultEnum result, Rule rule, @Nullable TranslationComponent comment) {
    public boolean hit() {
        return result == MatchResultEnum.TRUE || result == MatchResultEnum.BAN;
    }

    public boolean skip() {
        return result == MatchResultEnum.FALSE || result == MatchResultEnum.SKIP;
    }

    public boolean throttle() {
        return result == MatchResultEnum.THROTTLE;
    }
}
