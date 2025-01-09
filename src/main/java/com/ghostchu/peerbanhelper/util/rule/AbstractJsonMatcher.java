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

    @Override
    public @NotNull MatchResult match(@Nullable String content) {
        if (content == null) {
            content = "";
        }
        if (condition != null) {
            if (condition.match(content).result() == MatchResultEnum.FALSE) {
                return new MatchResult(MatchResultEnum.FALSE,
                        new TranslationComponent(Lang.JSON_MATCHER_NOT_MET,
                                condition.toPrintableText(Main.DEF_LOCALE),
                                toPrintableText(Main.DEF_LOCALE)));
            }
        }
        return match0(content);
    }
}
