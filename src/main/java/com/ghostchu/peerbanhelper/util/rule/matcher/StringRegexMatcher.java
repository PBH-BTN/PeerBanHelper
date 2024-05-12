package com.ghostchu.peerbanhelper.util.rule.matcher;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.rule.AbstractMatcher;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.google.gson.JsonObject;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.regex.Pattern;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StringRegexMatcher extends AbstractMatcher {
    private final Pattern rule;
    private MatchResult hit = MatchResult.TRUE;
    private MatchResult miss = MatchResult.DEFAULT;

    public StringRegexMatcher(JsonObject syntax) {
        super(syntax);
        this.rule = Pattern.compile(syntax.get("content").getAsString());
        if (syntax.has("hit")) {
            this.hit = MatchResult.valueOf(syntax.get("hit").getAsString());
        }
        if (syntax.has("miss")) {
            this.miss = MatchResult.valueOf(syntax.get("miss").getAsString());
        }
    }

    @Override
    public @NotNull MatchResult match0(@NotNull String content) {
        if (rule.matcher(content).matches()) {
            return hit;
        } else {
            return miss;
        }
    }

    @Override
    public String matcherIdentifier() {
        return "peerbanhelper:stringregexmatcher";
    }

    @Override
    public @NotNull String matcherName() {
        return Lang.RULE_MATCHER_STRING_REGEX;
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("rule", rule);
    }
}
