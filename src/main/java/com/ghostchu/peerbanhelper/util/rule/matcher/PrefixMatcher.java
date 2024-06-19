package com.ghostchu.peerbanhelper.util.rule.matcher;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleMatcher;
import com.ghostchu.peerbanhelper.util.rule.RuleType;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PrefixMatcher extends RuleMatcher {

    private List<String> prefixes;

    public PrefixMatcher(RuleType ruleType, String ruleId, String ruleName, Object... ruleData) {
        super(ruleType, ruleId, ruleName, ruleData);
    }

    @Override
    public void setData(String ruleName, Object... ruleData) {
        this.ruleName = ruleName;
        this.prefixes = ruleData.length > 0 ? (List<String>) ruleData[0] : List.of();
    }

    @Override
    public @NotNull MatchResult match0(@NotNull String content) {
        if (prefixes.parallelStream().anyMatch(content::startsWith)) {
            return MatchResult.TRUE;
        }
        return MatchResult.DEFAULT;
    }

    @Override
    public @NotNull String matcherName() {
        return Lang.RULE_MATCHER_SUB_RULE;
    }

    @Override
    public String matcherIdentifier() {
        return "peerbanhelper:prefixmatcher";
    }
}
