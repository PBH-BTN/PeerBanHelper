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
public class SubStrMatcher extends RuleMatcher {

    private List<String> subStrs;

    public SubStrMatcher(RuleType ruleType, String ruleId, String ruleName, Object... ruleData) {
        super(ruleType, ruleId, ruleName, ruleData);
    }

    @Override
    public void setData(String ruleName, Object... ruleData) {
        this.ruleName = ruleName;
        this.subStrs = ruleData.length > 0 ? (List<String>) ruleData[0] : List.of();
    }

    @Override
    public @NotNull MatchResult match0(@NotNull String content) {
        if (subStrs.parallelStream().anyMatch(content::contains)) {
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
        return "peerbanhelper:substrmatcher";
    }
}
