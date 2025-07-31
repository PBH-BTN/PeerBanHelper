package com.ghostchu.peerbanhelper.module.impl.rule.subscription.matcher;

import com.ghostchu.peerbanhelper.module.impl.rule.subscription.RuleType;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 子串匹配规则匹配器
 * Substring matching rule matcher
 */
public class SubstringMatcher extends EnhancedRuleMatcher {
    private Set<String> patterns;
    
    public SubstringMatcher(String ruleId, String ruleName, List<String> data) {
        super(ruleId, ruleName, RuleType.SUBSTRING_MATCH);
        this.patterns = new HashSet<>(data);
    }
    
    @Override
    public @NotNull MatchResult match(@Nullable String content) {
        if (content == null || content.isEmpty()) {
            return createMatchResult(false, null);
        }
        
        for (String pattern : patterns) {
            if (content.contains(pattern)) {
                TranslationComponent comment = new TranslationComponent("Matched substring: " + pattern);
                return createMatchResult(true, comment);
            }
        }
        
        return createMatchResult(false, null);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void setData(String ruleName, List<?> data) {
        this.ruleName = ruleName;
        if (!data.isEmpty() && data.get(0) instanceof List) {
            this.patterns = new HashSet<>((List<String>) data.get(0));
        }
    }
    
    @Override
    public int getDataSize() {
        return patterns != null ? patterns.size() : 0;
    }
}