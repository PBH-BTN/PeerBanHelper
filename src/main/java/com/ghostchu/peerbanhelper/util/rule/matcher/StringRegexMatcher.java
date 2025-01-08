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
    private MatchResult hit = new MatchResult(MatchResultEnum.TRUE, new TranslationComponent(Lang.MATCH_STRING_REGEX, "<Not Provided>"));
    private MatchResult miss = new MatchResult(MatchResultEnum.DEFAULT, new TranslationComponent(Lang.MATCH_STRING_REGEX, "<Not Provided>"));

    /**
     * Constructs a StringRegexMatcher with a specified JSON configuration for regex matching.
     *
     * @param syntax A JsonObject containing the regex configuration
     *               Required key: "content" - the regex pattern to compile
     *               Optional keys: "hit" - custom match result when regex matches
     *                              "miss" - custom match result when regex does not match
     * @throws IllegalArgumentException if the "content" key is missing or invalid
     */
    public StringRegexMatcher(JsonObject syntax) {
        super(syntax);
        this.rule = Pattern.compile(syntax.get("content").getAsString());
        if (syntax.has("hit")) {
            this.hit = new MatchResult(MatchResultEnum.valueOf(syntax.get("hit").getAsString()), new TranslationComponent(Lang.MATCH_STRING_REGEX, "Hit=" + rule.pattern()));
        }
        if (syntax.has("miss")) {
            this.miss = new MatchResult(MatchResultEnum.valueOf(syntax.get("miss").getAsString()), new TranslationComponent(Lang.MATCH_STRING_REGEX, "Miss=" + rule.pattern()));
        }
    }

    /**
     * Matches the provided content against a pre-compiled regular expression pattern.
     *
     * @param content The string to be matched against the regex pattern
     * @return A {@code MatchResult} indicating whether the content matches the pattern
     *         - Returns {@code hit} if the content fully matches the regex pattern
     *         - Returns {@code miss} if the content does not match the regex pattern
     * @throws NullPointerException if the content is null
     */
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
        return nameComponent;
    }

    @Override
    public String metadata() {
        return rule.pattern();
    }
}
