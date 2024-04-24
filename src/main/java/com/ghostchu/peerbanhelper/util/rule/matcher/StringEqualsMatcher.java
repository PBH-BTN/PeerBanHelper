package com.ghostchu.peerbanhelper.util.rule.matcher;

import com.ghostchu.peerbanhelper.util.rule.AbstractMatcher;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

public class StringEqualsMatcher extends AbstractMatcher {
    private final String rule;
    private MatchResult success = MatchResult.POSITIVE;
    private MatchResult failure = MatchResult.NEUTRAL;

    public StringEqualsMatcher(JsonObject syntax) {
        super(syntax);
        this.rule = syntax.get("content").getAsString();
        if (syntax.has("success")) {
            this.success = MatchResult.valueOf(syntax.get("success").getAsString());
        }
        if (syntax.has("failure")) {
            this.failure = MatchResult.valueOf(syntax.get("failure").getAsString());
        }
    }

    @Override
    public @NotNull MatchResult match(@NotNull String content) {
        if (super.match(content) == MatchResult.NEGATIVE) {
            return MatchResult.NEUTRAL;
        }
        if (content.equalsIgnoreCase(rule)) {
            return success;
        } else {
            return failure;
        }
    }

    @Override
    public String toString() {
        return "StringEqualsMatcher{" +
                "rule='" + rule + '\'' +
                ", success=" + success +
                ", failure=" + failure +
                '}';
    }
}
