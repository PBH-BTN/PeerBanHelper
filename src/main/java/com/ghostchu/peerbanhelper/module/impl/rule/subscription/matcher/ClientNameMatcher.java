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
 * 客户端名称规则匹配器
 * Client name rule matcher
 */
public class ClientNameMatcher extends EnhancedRuleMatcher {
    private Set<String> clientNames;
    
    public ClientNameMatcher(String ruleId, String ruleName, List<String> data) {
        super(ruleId, ruleName, RuleType.CLIENT_NAME);
        this.clientNames = new HashSet<>(data);
    }
    
    @Override
    public @NotNull MatchResult match(@Nullable String content) {
        if (content == null || content.isEmpty()) {
            return createMatchResult(false, null);
        }
        
        boolean matched = clientNames.contains(content);
        TranslationComponent comment = matched ? 
            new TranslationComponent("Matched client name: " + content) : null;
        
        return createMatchResult(matched, comment);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void setData(String ruleName, List<?> data) {
        this.ruleName = ruleName;
        if (!data.isEmpty() && data.get(0) instanceof List) {
            this.clientNames = new HashSet<>((List<String>) data.get(0));
        }
    }
    
    @Override
    public int getDataSize() {
        return clientNames != null ? clientNames.size() : 0;
    }
}