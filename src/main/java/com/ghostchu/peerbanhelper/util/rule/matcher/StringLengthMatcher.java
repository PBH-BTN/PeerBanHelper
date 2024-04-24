package com.ghostchu.peerbanhelper.util.rule.matcher;

import com.ghostchu.peerbanhelper.util.rule.AbstractMatcher;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

public class StringLengthMatcher extends AbstractMatcher {
    private final int min;
    private final int max;
    private MatchResult success = MatchResult.POSITIVE;
    private MatchResult failure = MatchResult.NEUTRAL;

    public StringLengthMatcher(JsonObject syntax) {
        super(syntax);
        this.min = syntax.get("min").getAsInt();
        this.max = syntax.get("max").getAsInt();
        if (syntax.has("success")) {
            this.success = MatchResult.valueOf(syntax.get("success").getAsString());
        }
        if (syntax.has("failure")) {
            this.failure = MatchResult.valueOf(syntax.get("failure").getAsString());
        }
    }

    @Override
    public @NotNull MatchResult match0(@NotNull String content) {
        int length = content.length();
        return (length >= min && length <= max) ? this.success : this.failure;
    }

    @Override
    public String toString() {
        return "StringLengthMatcher{" +
                "min=" + min +
                ", max=" + max +
                ", success=" + success +
                ", failure=" + failure +
                '}';
    }
}
