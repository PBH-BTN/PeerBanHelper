package com.ghostchu.peerbanhelper.util.rule.matcher;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.rule.AbstractJsonMatcher;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.google.gson.JsonObject;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StringContainsMatcher extends AbstractJsonMatcher {
    private final String rule;
    private MatchResult hit = MatchResult.TRUE;
    private MatchResult miss = MatchResult.DEFAULT;

    public StringContainsMatcher(JsonObject syntax) {
        super(syntax);
        this.rule = syntax.get("content").getAsString().toLowerCase(Locale.ROOT);
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
        if (content.contains(rule)) {
            return hit;
        } else {
            return miss;
        }
    }

    @Override
    public @NotNull String matcherName() {
        return tlUI(Lang.RULE_MATCHER_STRING_CONTAINS);
    }

    @Override
    public String matcherIdentifier() {
        return "peerbanhelper:stringcontainsmatcher";
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("rule", rule);
    }
}
