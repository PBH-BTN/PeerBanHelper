package com.ghostchu.peerbanhelper.util.rule.matcher;

import com.ghostchu.peerbanhelper.api.text.Lang;
import com.ghostchu.peerbanhelper.api.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.rule.AbstractJsonMatcher;
import com.ghostchu.peerbanhelper.api.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.api.util.rule.MatchResultEnum;
import com.google.gson.JsonObject;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public final class StringContainsMatcher extends AbstractJsonMatcher {
    private final String rule;
    private MatchResult hit;
    private MatchResult miss;

    public StringContainsMatcher(JsonObject syntax) {
        super(syntax);
        this.rule = syntax.get("content").getAsString().toLowerCase(Locale.ROOT);
        this.hit = new MatchResult(MatchResultEnum.TRUE, new TranslationComponent(Lang.MATCH_STRING_CONTAINS, rule));
        this.miss = new MatchResult(MatchResultEnum.DEFAULT, new TranslationComponent(Lang.MATCH_STRING_CONTAINS, rule));
        if (syntax.has("hit")) {
            this.hit = new MatchResult(MatchResultEnum.valueOf(syntax.get("hit").getAsString()), new TranslationComponent(Lang.MATCH_STRING_CONTAINS, "Hit-" + rule));
        }
        if (syntax.has("miss")) {
            this.miss = new MatchResult(MatchResultEnum.valueOf(syntax.get("miss").getAsString()), new TranslationComponent(Lang.MATCH_STRING_CONTAINS, "Miss-" + rule));
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
    public TranslationComponent matcherName() {
        return new TranslationComponent(Lang.MATCH_STRING_CONTAINS, rule);
    }

    @Override
    public String matcherIdentifier() {
        return "peerbanhelper:stringcontainsmatcher";
    }

    @Override
    public String metadata() {
        return rule;
    }
}
