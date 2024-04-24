package com.ghostchu.peerbanhelper.util.rule;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

public class AbstractMatcher implements Rule {
    private Rule condition;

    public AbstractMatcher(JsonObject rule) {
        if (rule.has("if")) {
            this.condition = RuleParser.parse(rule.get("if"));
        }
    }

    @Override
    public @NotNull MatchResult match(@NotNull String content) {
        if (condition != null) {
            if (condition.match(content) == MatchResult.NEGATIVE) {
                return MatchResult.NEGATIVE;
            }
        }
        return MatchResult.NEUTRAL;
    }
}
