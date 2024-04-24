package com.ghostchu.peerbanhelper.util.rule;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

public class AbstractMatcher implements Rule {
    private Rule condition;
    private long queryCounter;
    private long hitCounter;

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

    @Override
    public long getQueryCounter() {
        return queryCounter;
    }

    @Override
    public void addQueryCount() {
        queryCounter++;
    }

    @Override
    public long getHitCounter() {
        return hitCounter;
    }

    @Override
    public void addHitCount() {
        hitCounter++;
    }
}
