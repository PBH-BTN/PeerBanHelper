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
    private MatchResult hit = new MatchResult(MatchResultEnum.TRUE, new TranslationComponent(Lang.MATCH_STRING_EQUALS, "<Not Provided>"));
    private MatchResult miss = new MatchResult(MatchResultEnum.DEFAULT, new TranslationComponent(Lang.MATCH_STRING_EQUALS, "<Not Provided>"));

    /**
     * Constructs a StringEqualsMatcher with configuration from a JSON object.
     *
     * @param syntax A JsonObject containing matcher configuration parameters
     *               Expected keys:
     *               - "content": The string to match against (required)
     *               - "hit": Optional match result when a match occurs
     *               - "miss": Optional match result when no match occurs
     *
     * @throws IllegalArgumentException if the required "content" field is missing
     */
    public StringEqualsMatcher(JsonObject syntax) {
        super(syntax);
        this.rule = syntax.get("content").getAsString();
        if (syntax.has("hit")) {
            this.hit = new MatchResult(MatchResultEnum.valueOf(syntax.get("hit").getAsString()), new TranslationComponent(Lang.MATCH_STRING_EQUALS, "Hit-" + rule));
        }
        if (syntax.has("miss")) {
            this.miss = new MatchResult(MatchResultEnum.valueOf(syntax.get("miss").getAsString()), new TranslationComponent(Lang.MATCH_STRING_EQUALS, "Miss-" + rule));
        }
    }

    /**
     * Performs a case-insensitive string comparison between the provided content and the predefined rule.
     *
     * @param content The input string to be matched against the rule
     * @return A {@code MatchResult} indicating whether the strings match:
     *         - Returns the pre-configured {@code hit} result if strings match (case-insensitive)
     *         - Returns the pre-configured {@code miss} result if strings do not match
     * @throws NullPointerException if the input content is null
     */
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
        return nameComponent;
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
