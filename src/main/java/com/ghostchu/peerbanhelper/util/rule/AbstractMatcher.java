package com.ghostchu.peerbanhelper.util.rule;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractMatcher extends BasicRule {
    private Rule condition;

    public AbstractMatcher(JsonObject rule) {
        if (rule.has("if")) {
            this.condition = RuleParser.parse(rule.get("if"));
        }
    }

    @Override
    public @NotNull MatchResult match(@NotNull String content) {
        recordQuery();
        if (condition != null) {
            if (condition.match(content) == MatchResult.NEGATIVE) {
                recordHit();
                return MatchResult.NEGATIVE;
            }
        }
        MatchResult r = match0(content);
        if (r != MatchResult.NEUTRAL) {
            recordHit();
        }
        return r;
    }

    public abstract @NotNull MatchResult match0(@NotNull String content);
}
