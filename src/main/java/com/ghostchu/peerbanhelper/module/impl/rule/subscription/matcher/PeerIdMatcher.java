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
 * PeerID规则匹配器
 * PeerID rule matcher
 */
public class PeerIdMatcher extends EnhancedRuleMatcher {
    private Set<String> peerIds;
    
    public PeerIdMatcher(String ruleId, String ruleName, List<String> data) {
        super(ruleId, ruleName, RuleType.PEER_ID);
        this.peerIds = new HashSet<>(data);
    }
    
    @Override
    public @NotNull MatchResult match(@Nullable String content) {
        if (content == null || content.isEmpty()) {
            return createMatchResult(false, null);
        }
        
        boolean matched = peerIds.contains(content);
        TranslationComponent comment = matched ? 
            new TranslationComponent("Matched PeerID: " + content) : null;
        
        return createMatchResult(matched, comment);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void setData(String ruleName, List<?> data) {
        this.ruleName = ruleName;
        if (!data.isEmpty() && data.get(0) instanceof List) {
            this.peerIds = new HashSet<>((List<String>) data.get(0));
        }
    }
    
    @Override
    public int getDataSize() {
        return peerIds != null ? peerIds.size() : 0;
    }
}