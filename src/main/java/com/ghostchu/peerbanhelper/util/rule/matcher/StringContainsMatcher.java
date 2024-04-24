package com.ghostchu.peerbanhelper.util.rule.matcher;

import com.ghostchu.peerbanhelper.util.rule.AbstractMatcher;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class StringContainsMatcher extends AbstractMatcher {
    private final String rule;
    private MatchResult success = MatchResult.POSITIVE;
    private MatchResult failure = MatchResult.NEUTRAL;

    public StringContainsMatcher(JsonObject syntax) {
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
    public @NotNull MatchResult match0(@NotNull String content) {
        content = content.toLowerCase(Locale.ROOT);
        if (content.contains(rule)) {
            return success;
        } else {
            return failure;
        }
    }

    @Override
    public String toString() {
        return "StringContainsMatcher{" +
                "rule='" + rule + '\'' +
                ", success=" + success +
                ", failure=" + failure +
                '}';
    }
}
