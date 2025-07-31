package com.ghostchu.peerbanhelper.module.impl.rule.subscription.matcher;

import com.ghostchu.peerbanhelper.module.impl.rule.subscription.RuleType;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import inet.ipaddr.IPAddress;
import inet.ipaddr.format.util.DualIPv4v6AssociativeTries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * IP黑名单规则匹配器 - 使用内存优化的存储结构
 * IP blacklist rule matcher - uses memory-optimized storage structure
 */
public class EnhancedIPMatcher extends EnhancedRuleMatcher {
    private DualIPv4v6AssociativeTries<String> ipTries;
    
    public EnhancedIPMatcher(String ruleId, String ruleName, List<DualIPv4v6AssociativeTries<String>> data) {
        super(ruleId, ruleName, RuleType.IP_BLACKLIST);
        if (!data.isEmpty()) {
            this.ipTries = data.get(0);
        } else {
            this.ipTries = new DualIPv4v6AssociativeTries<>();
        }
    }
    
    @Override
    public @NotNull MatchResult match(@Nullable String content) {
        if (content == null || content.isEmpty()) {
            return createMatchResult(false, null);
        }
        
        try {
            IPAddress ipAddress = IPAddressUtil.getIPAddress(content);
            if (ipAddress == null) {
                return createMatchResult(false, null);
            }
            
            String comment = ipTries.get(ipAddress);
            boolean matched = comment != null;
            
            TranslationComponent commentComponent = comment != null ? 
                new TranslationComponent(comment) : null;
            
            return createMatchResult(matched, commentComponent);
        } catch (Exception e) {
            return createMatchResult(false, null);
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public void setData(String ruleName, List<?> data) {
        this.ruleName = ruleName;
        if (!data.isEmpty() && data.get(0) instanceof DualIPv4v6AssociativeTries) {
            this.ipTries = (DualIPv4v6AssociativeTries<String>) data.get(0);
        }
    }
    
    @Override
    public int getDataSize() {
        return ipTries != null ? ipTries.size() : 0;
    }
}