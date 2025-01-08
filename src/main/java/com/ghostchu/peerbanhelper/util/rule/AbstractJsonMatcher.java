package com.ghostchu.peerbanhelper.util.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.google.gson.JsonObject;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@EqualsAndHashCode(callSuper = true)
@ToString
public abstract class AbstractJsonMatcher extends AbstractMatcher {
    private Rule condition;

    public AbstractJsonMatcher(JsonObject rule) {
        if (rule.has("if")) {
            this.condition = RuleParser.parse(rule.get("if"));
        }
    }

    /**
     * Matches the given content against a JSON-based condition.
     *
     * @param content The input content to match, which can be null
     * @return A {@code MatchResult} indicating the outcome of the matching process
     *
     * This method first normalizes null content to an empty string. If a condition is defined,
     * it evaluates the content against the condition. If the condition is not met, a failure
     * match result is returned with a translation component explaining the condition mismatch.
     * Otherwise, it delegates to the {@code match0} method for further matching logic.
     *
     * @throws No exceptions are thrown directly by this method
     */
    @Override
    public @NotNull MatchResult match(@Nullable String content) {
        if (content == null) {
            content = "";
        }
        if (condition != null) {
            if (condition.match(content).result() == MatchResultEnum.FALSE) {
                return new MatchResult(MatchResultEnum.FALSE, new TranslationComponent(Lang.JSON_MATCHER_NOT_MET, condition.toPrintableText(Main.DEF_LOCALE), toPrintableText(Main.DEF_LOCALE)));
            }
        }
        return match0(content);
    }
}
