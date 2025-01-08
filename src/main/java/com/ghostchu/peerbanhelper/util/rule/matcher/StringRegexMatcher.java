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
     * @param syntax A JsonObject containing the configuration for the regex matcher
     *               The JsonObject must contain a "content" field with the regex pattern
     *               Optional "hit" and "miss" fields can customize match result behavior
     * @throws IllegalArgumentException if the "content" field is missing or invalid
     * @throws PatternSyntaxException if the regex pattern is malformed
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
     * Matches the given content against a pre-compiled regular expression pattern.
     *
     * @param content The string to be matched against the regex pattern
     * @return A {@code MatchResult} indicating whether the content matches the pattern
     *         - Returns the pre-configured 'hit' result if the content matches the regex
     *         - Returns the pre-configured 'miss' result if the content does not match the regex
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
