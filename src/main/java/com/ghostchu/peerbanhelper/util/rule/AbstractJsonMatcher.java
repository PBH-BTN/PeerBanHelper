package com.ghostchu.peerbanhelper.util.rule;

import com.ghostchu.peerbanhelper.Main;
import com.google.gson.JsonObject;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@EqualsAndHashCode(callSuper = true)
@ToString
public abstract class AbstractJsonMatcher extends AbstractMatcher {
    private Rule condition;

    public AbstractJsonMatcher(JsonObject rule) {
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
}
