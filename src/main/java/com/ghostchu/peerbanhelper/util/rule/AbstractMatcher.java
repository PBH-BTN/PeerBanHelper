package com.ghostchu.peerbanhelper.util.rule;

import com.ghostchu.peerbanhelper.Main;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractMatcher implements Rule {
    private Rule condition;

    public AbstractMatcher(JsonObject rule) {
        if (rule.has("if")) {
            this.condition = RuleParser.parse(rule.get("if"));
        }
    }

    @Override
    public @NotNull MatchResult match(@Nullable String content) {
        Main.getServer().getHitRateMetric().addQuery(this);
        if (content == null) {
            content = "";
        }
        if (condition != null) {
            if (condition.match(content) == MatchResult.FALSE) {
                Main.getServer().getHitRateMetric().addHit(this);
                return MatchResult.FALSE;
            }
        }
        MatchResult r = match0(content);
        if (r != MatchResult.DEFAULT) {
            Main.getServer().getHitRateMetric().addHit(this);
        }
        return r;
    }

    public abstract @NotNull MatchResult match0(@NotNull String content);
}
