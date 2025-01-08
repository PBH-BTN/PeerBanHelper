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
public final class StringLengthMatcher extends AbstractJsonMatcher {
    private static final TranslationComponent nameComponent = new TranslationComponent(Lang.RULE_MATCHER_STRING_LENGTH);
    private final int min;
    private final int max;
    private MatchResult hit = new MatchResult(MatchResultEnum.TRUE, new TranslationComponent(Lang.MATCH_STRING_LENGTH, "<Not Provided>"));
    private MatchResult miss = new MatchResult(MatchResultEnum.DEFAULT, new TranslationComponent(Lang.MATCH_STRING_LENGTH, "<Not Provided>"));

    /**
     * Constructs a StringLengthMatcher with configuration from a JSON object.
     *
     * @param syntax A JsonObject containing configuration for string length matching
     *               Expected keys:
     *               - "min": Minimum allowed string length (required)
     *               - "max": Maximum allowed string length (required)
     *               - "hit": Optional match result when length is within range
     *               - "miss": Optional match result when length is outside range
     * @throws IllegalArgumentException if "min" or "max" keys are missing or invalid
     */
    public StringLengthMatcher(JsonObject syntax) {
        super(syntax);
        this.min = syntax.get("min").getAsInt();
        this.max = syntax.get("max").getAsInt();
        if (syntax.has("hit")) {
            this.hit = new MatchResult(MatchResultEnum.valueOf(syntax.get("hit").getAsString()), new TranslationComponent(Lang.MATCH_STRING_LENGTH, "Hit-Min-" + min + ", Max-" + max));
        }
        if (syntax.has("miss")) {
            this.miss = new MatchResult(MatchResultEnum.valueOf(syntax.get("miss").getAsString()), new TranslationComponent(Lang.MATCH_STRING_LENGTH, "Miss-Min-" + min + ", Max-" + max));
        }
    }

    /**
     * Determines whether the input string's length matches the predefined minimum and maximum length constraints.
     *
     * @param content The string to be evaluated for length matching
     * @return A {@code MatchResult} indicating whether the string length is within the specified range
     *         - Returns {@code hit} if the string length is between or equal to the minimum and maximum values
     *         - Returns {@code miss} if the string length falls outside the defined range
     * @throws NullPointerException if the input content is null
     */
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
