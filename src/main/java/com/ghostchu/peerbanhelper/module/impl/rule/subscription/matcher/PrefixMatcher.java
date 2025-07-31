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
 * 前缀匹配规则匹配器
 * Prefix matching rule matcher
 */
public class PrefixMatcher extends EnhancedRuleMatcher {
    private Set<String> prefixes;
    
    public PrefixMatcher(String ruleId, String ruleName, List<String> data) {
        super(ruleId, ruleName, RuleType.PREFIX_MATCH);
        this.prefixes = new HashSet<>(data);
    }
    
    @Override
    public @NotNull MatchResult match(@Nullable String content) {
        if (content == null || content.isEmpty()) {
            return createMatchResult(false, null);
        }
        
        for (String prefix : prefixes) {
            if (content.startsWith(prefix)) {
                TranslationComponent comment = new TranslationComponent("Matched prefix: " + prefix);
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
            this.prefixes = new HashSet<>((List<String>) data.get(0));
        }
    }
    
    @Override
    public int getDataSize() {
        return prefixes != null ? prefixes.size() : 0;
    }
}