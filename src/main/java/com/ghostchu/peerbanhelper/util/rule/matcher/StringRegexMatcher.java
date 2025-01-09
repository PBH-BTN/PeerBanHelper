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

import java.util.regex.Pattern;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public final class StringRegexMatcher extends AbstractJsonMatcher {
    private static final TranslationComponent nameComponent = new TranslationComponent(Lang.RULE_MATCHER_STRING_REGEX);
    private final Pattern rule;
    private MatchResult hit;
    private MatchResult miss;

    public StringRegexMatcher(JsonObject syntax) {
        super(syntax);
        this.rule = Pattern.compile(syntax.get("content").getAsString());
        this.hit = new MatchResult(MatchResultEnum.TRUE, new TranslationComponent(Lang.MATCH_STRING_REGEX, rule));
        this.miss = new MatchResult(MatchResultEnum.DEFAULT, new TranslationComponent(Lang.MATCH_STRING_REGEX, rule));
        if (syntax.has("hit")) {
            this.hit = new MatchResult(MatchResultEnum.valueOf(syntax.get("hit").getAsString()), new TranslationComponent(Lang.MATCH_STRING_REGEX, "Hit=" + rule.pattern()));
        }
        if (syntax.has("miss")) {
            this.miss = new MatchResult(MatchResultEnum.valueOf(syntax.get("miss").getAsString()), new TranslationComponent(Lang.MATCH_STRING_REGEX, "Miss=" + rule.pattern()));
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
    public TranslationComponent matcherName() {
        return new TranslationComponent(Lang.MATCH_STRING_REGEX, rule);
    }

    @Override
    public String metadata() {
        return rule.pattern();
    }
}
