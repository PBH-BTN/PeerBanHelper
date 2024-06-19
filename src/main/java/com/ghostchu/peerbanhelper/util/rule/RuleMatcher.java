package com.ghostchu.peerbanhelper.util.rule;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * 规则Matcher
 */
@Getter
@Slf4j
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class RuleMatcher extends AbstractMatcher {

    private final String ruleId;

    private final SubRuleType ruleType;

    protected String ruleName;

    public RuleMatcher(SubRuleType ruleType, String ruleId, String ruleName, Object... ruleData) {
        this.ruleType = ruleType;
        this.ruleId = ruleId;
        setData(ruleName, ruleData);
    }

    public abstract void setData(String ruleName, Object... ruleData);

}
