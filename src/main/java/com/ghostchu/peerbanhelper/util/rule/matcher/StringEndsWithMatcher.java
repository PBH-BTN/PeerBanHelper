package com.ghostchu.peerbanhelper.util.rule.matcher;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.rule.AbstractMatcher;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;

public class StringEndsWithMatcher extends AbstractMatcher {
    private final String rule;
    private MatchResult hit = MatchResult.TRUE;
    private MatchResult miss = MatchResult.DEFAULT;

    public StringEndsWithMatcher(JsonObject syntax) {
        super(syntax);
        this.rule = syntax.get("content").getAsString();
        if (syntax.has("hit")) {
            this.hit = MatchResult.valueOf(syntax.get("hit").getAsString());
        }
        if (syntax.has("miss")) {
            this.miss = MatchResult.valueOf(syntax.get("miss").getAsString());
        }
    }

    @Override
    public @NotNull MatchResult match0(@NotNull String content) {
        content = content.toLowerCase(Locale.ROOT);
        if (content.endsWith(rule)) {
            return hit;
        } else {
            return miss;
        }
    }

    @Override
    public @NotNull String matcherName() {
        return Lang.RULE_MATCHER_STRING_ENDS_WITH;
    }

    @Override
    public String toString() {
        return "StringEndsWithMatcher{" +
                "rule='" + rule + '\'' +
                ", hit=" + hit +
                ", miss=" + miss +
                '}';
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("rule", rule);
    }
}
