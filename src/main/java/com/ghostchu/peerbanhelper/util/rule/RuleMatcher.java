package com.ghostchu.peerbanhelper.util.rule;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * 规则Matcher
 */
@Getter
@Slf4j
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class RuleMatcher<T> extends AbstractMatcher {

    private final String ruleId;

    @Setter
    private String ruleName;

    public RuleMatcher(@NotNull String ruleId, @NotNull String ruleName, @NotNull List<T> ruleData) {
        this.ruleId = ruleId;
        setData(ruleName, ruleData);
    }

    public abstract void setData(@NotNull String ruleName, @NotNull List<T> ruleData);

    @NotNull
    public Map<String, Object> metadata() {
        return Map.of("id", ruleId, "rule", ruleName);
    }

}
