package com.ghostchu.peerbanhelper.util.rule.matcher;

import com.ghostchu.peerbanhelper.util.rule.AbstractMatcher;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class StringRegexMatcher extends AbstractMatcher {
    private final Pattern rule;
    private MatchResult success = MatchResult.POSITIVE;
    private MatchResult failure = MatchResult.NEUTRAL;

    public StringRegexMatcher(JsonObject syntax) {
        super(syntax);
        this.rule = Pattern.compile(syntax.get("content").getAsString());
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
        if (rule.matcher(content).matches()) {
            return success;
        } else {
            return failure;
        }
    }

    @Override
    public String toString() {
        return "StringRegexMatcher{" +
                "rule=" + rule +
                ", success=" + success +
                ", failure=" + failure +
                '}';
    }
}
