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
public final class StringContainsMatcher extends AbstractJsonMatcher {
    private static final TranslationComponent nameComponent = new TranslationComponent(Lang.RULE_MATCHER_STRING_CONTAINS);
    private final String rule;
    private MatchResult hit = new MatchResult(MatchResultEnum.TRUE, new TranslationComponent(Lang.MATCH_STRING_CONTAINS, "<Not Provided>"));
    private MatchResult miss = new MatchResult(MatchResultEnum.DEFAULT, new TranslationComponent(Lang.MATCH_STRING_CONTAINS, "<Not Provided>"));

    /**
     * Constructs a StringContainsMatcher with configuration from a JSON object.
     *
     * @param syntax A JsonObject containing matcher configuration parameters
     *               - "content": The string to match against (required)
     *               - "hit": Optional match result when content is found
     *               - "miss": Optional match result when content is not found
     *
     * @throws IllegalArgumentException if the required "content" field is missing
     */
    public StringContainsMatcher(JsonObject syntax) {
        super(syntax);
        this.rule = syntax.get("content").getAsString().toLowerCase(Locale.ROOT);
        if (syntax.has("hit")) {
            this.hit = new MatchResult(MatchResultEnum.valueOf(syntax.get("hit").getAsString()), new TranslationComponent(Lang.MATCH_STRING_CONTAINS, "Hit-" + rule));
        }
        if (syntax.has("miss")) {
            this.miss = new MatchResult(MatchResultEnum.valueOf(syntax.get("miss").getAsString()), new TranslationComponent(Lang.MATCH_STRING_CONTAINS, "Miss-" + rule));
        }
    }

    /**
     * Matches the given content against a predefined rule by checking for substring containment.
     *
     * <p>This method performs a case-insensitive substring search by converting both the input
     * content and the rule to lowercase before comparison.</p>
     *
     * @param content The input string to be matched against the rule
     * @return A {@code MatchResult} indicating whether the content contains the rule:
     *         - Returns {@code hit} if the rule is found in the content
     *         - Returns {@code miss} if the rule is not found in the content
     * @throws NullPointerException if the input content is null
     */
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
        return nameComponent;
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
