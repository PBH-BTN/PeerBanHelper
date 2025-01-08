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

import java.util.Locale;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public final class StringEndsWithMatcher extends AbstractJsonMatcher {
    private static final TranslationComponent nameComponent = new TranslationComponent(Lang.RULE_MATCHER_STRING_ENDS_WITH);
    private final String rule;
    private MatchResult hit = new MatchResult(MatchResultEnum.TRUE, new TranslationComponent(Lang.MATCH_STRING_ENDS_WITH, "<Not Provided>"));
    private MatchResult miss = new MatchResult(MatchResultEnum.DEFAULT, new TranslationComponent(Lang.MATCH_STRING_ENDS_WITH, "<Not Provided>"));

    /**
     * Constructs a StringEndsWithMatcher with configuration from a JSON object.
     *
     * @param syntax A JsonObject containing matcher configuration parameters
     *               - "content": The substring to match at the end of a string (required)
     *               - "hit": Optional custom match result when a match is found
     *               - "miss": Optional custom match result when no match is found
     *
     * @throws IllegalArgumentException if the required "content" field is missing
     *
     * The matcher will convert the content to lowercase for case-insensitive matching.
     * If "hit" or "miss" are not specified, default match results will be used.
     */
    public StringEndsWithMatcher(JsonObject syntax) {
        super(syntax);
        this.rule = syntax.get("content").getAsString().toLowerCase(Locale.ROOT);
        if (syntax.has("hit")) {
            this.hit = new MatchResult(MatchResultEnum.valueOf(syntax.get("hit").getAsString()), new TranslationComponent(Lang.MATCH_STRING_ENDS_WITH, "Hit-" + rule));
        }
        if (syntax.has("miss")) {
            this.miss = new MatchResult(MatchResultEnum.valueOf(syntax.get("miss").getAsString()), new TranslationComponent(Lang.MATCH_STRING_ENDS_WITH, "Miss-" + rule));
        }
    }

    /**
     * Determines if the given content ends with the predefined rule string.
     *
     * @param content The input string to be matched, which will be converted to lowercase
     * @return A {@code MatchResult} indicating whether the content ends with the rule
     *         - Returns {@code hit} if the content ends with the rule
     *         - Returns {@code miss} if the content does not end with the rule
     * @throws NullPointerException if the input content is null
     */
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
    public TranslationComponent matcherName() {
        return nameComponent;
    }

    @Override
    public String matcherIdentifier() {
        return "peerbanhelper:stringendswithmatcher";
    }

    @Override
    public String metadata() {
        return rule;
    }
}
