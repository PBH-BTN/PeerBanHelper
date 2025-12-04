package com.ghostchu.peerbanhelper.util.rule.matcher;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.MatchResultEnum;
import com.ghostchu.peerbanhelper.util.rule.RuleMatcher;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * PeerName matcher using regex patterns for matching peer names from subscribed rules
 */
@Slf4j
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PeerNameMatcher extends RuleMatcher<List<Map.Entry<Pattern, String>>> {
    private List<Map.Entry<Pattern, String>> patterns;

    public PeerNameMatcher(String ruleId, String ruleName, List<List<Map.Entry<Pattern, String>>> ruleData) {
        super(ruleId, ruleName, ruleData);
        this.patterns = ruleData.isEmpty() ? List.of() : ruleData.getFirst();
    }

    @Override
    public void setData(String ruleName, List<List<Map.Entry<Pattern, String>>> ruleData) {
        setRuleName(ruleName);
        this.patterns = ruleData.isEmpty() ? List.of() : ruleData.getFirst();
    }

    public long size() {
        return patterns.size();
    }

    @Override
    public @NotNull MatchResult match0(@NotNull String content) {
        if (patterns == null || patterns.isEmpty()) {
            return new MatchResult(MatchResultEnum.DEFAULT, new TranslationComponent("Patterns set is empty"));
        }

        for (Map.Entry<Pattern, String> entry : patterns) {
            Pattern pattern = entry.getKey();
            String comment = entry.getValue();
            try {
                if (pattern.matcher(content).find()) {
                    return new MatchResult(
                            MatchResultEnum.TRUE,
                            new TranslationComponent(comment != null ? comment : pattern.pattern())
                    );
                }
            } catch (Exception e) {
                log.error("Error matching pattern: {}", pattern.pattern(), e);
            }
        }

        return new MatchResult(MatchResultEnum.DEFAULT, new TranslationComponent("Given PeerName not matched any pattern"));
    }

    @Override
    public TranslationComponent matcherName() {
        return new TranslationComponent(Lang.RULE_MATCHER_SUB_RULE, getRuleName());
    }

    @Override
    public String matcherIdentifier() {
        return "peerbanhelper:peernamematcher";
    }
}
