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
     * Matches the given content against a predefined JSON condition.
     *
     * This method first normalizes null content to an empty string, then checks if a condition exists.
     * If a condition is present and fails to match, it returns a negative match result with a translation component.
     * Otherwise, it delegates to the abstract {@code match0} method for further matching logic.
     *
     * @param content The input content to match, which may be null
     * @return A {@code MatchResult} indicating the outcome of the matching process
     * @throws NullPointerException if the returned match result is null
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
