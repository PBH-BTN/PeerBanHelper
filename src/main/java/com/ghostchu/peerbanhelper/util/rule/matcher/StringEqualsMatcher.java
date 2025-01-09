package com.ghostchu.peerbanhelper.util.rule.matcher;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.rule.AbstractJsonMatcher;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.MatchResultEnum;
import com.google.gson.JsonObject;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public final class StringEqualsMatcher extends AbstractJsonMatcher {
    private static final TranslationComponent nameComponent = new TranslationComponent(Lang.RULE_MATCHER_STRING_EQUALS);
    private final String rule;
    private MatchResult hit;
    private MatchResult miss;

    public StringEqualsMatcher(JsonObject syntax) {
        super(syntax);
        this.rule = syntax.get("content").getAsString();
        this.hit = new MatchResult(MatchResultEnum.TRUE, new TranslationComponent(Lang.MATCH_STRING_EQUALS, rule));
        this.miss = new MatchResult(MatchResultEnum.DEFAULT, new TranslationComponent(Lang.MATCH_STRING_EQUALS, rule));
        if (syntax.has("hit")) {
            this.hit = new MatchResult(MatchResultEnum.valueOf(syntax.get("hit").getAsString()), new TranslationComponent(Lang.MATCH_STRING_EQUALS, "Hit-" + rule));
        }
        if (syntax.has("miss")) {
            this.miss = new MatchResult(MatchResultEnum.valueOf(syntax.get("miss").getAsString()), new TranslationComponent(Lang.MATCH_STRING_EQUALS, "Miss-" + rule));
        }
    }

    @Override
    public @NotNull MatchResult match0(@NotNull String content) {
        if (content.equalsIgnoreCase(rule)) {
            return hit;
        } else {
            return miss;
        }
    }

    @Override
    public TranslationComponent matcherName() {
        return new TranslationComponent(Lang.MATCH_STRING_EQUALS, rule);
    }

    @Override
    public String matcherIdentifier() {
        return "peerbanhelper:stringequalsmatcher";
    }

    @Override
    public String metadata() {
        return rule;
    }
}
