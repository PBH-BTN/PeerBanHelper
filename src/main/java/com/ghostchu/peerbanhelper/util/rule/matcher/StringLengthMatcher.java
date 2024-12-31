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
public class StringLengthMatcher extends AbstractJsonMatcher {
    private static final TranslationComponent nameComponent = new TranslationComponent(Lang.RULE_MATCHER_STRING_LENGTH);
    private final int min;
    private final int max;
    private MatchResult hit = new MatchResult(MatchResultEnum.TRUE, "StringLength Hit");
    private MatchResult miss = new MatchResult(MatchResultEnum.FALSE, "StringLength Miss");

    public StringLengthMatcher(JsonObject syntax) {
        super(syntax);
        this.min = syntax.get("min").getAsInt();
        this.max = syntax.get("max").getAsInt();
        if (syntax.has("hit")) {
            this.hit = new MatchResult(MatchResultEnum.valueOf(syntax.get("hit").getAsString()), "StringLength Hit");
        }
        if (syntax.has("miss")) {
            this.miss = new MatchResult(MatchResultEnum.valueOf(syntax.get("miss").getAsString()), "StringLength Miss");
        }
    }

    @Override
    public @NotNull MatchResult match0(@NotNull String content) {
        int length = content.length();
        return (length >= min && length <= max) ? this.hit : this.miss;
    }

    @Override
    public TranslationComponent matcherName() {
        return nameComponent;
    }

    @Override
    public String matcherIdentifier() {
        return "peerbanhelper:stringlengthmatcher";
    }

    @Override
    public String metadata() {
        return String.format("min: %d, max: %d",min,max);
    }
}
