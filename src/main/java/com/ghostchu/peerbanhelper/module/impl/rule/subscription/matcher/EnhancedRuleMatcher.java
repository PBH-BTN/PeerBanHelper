package com.ghostchu.peerbanhelper.module.impl.rule.subscription.matcher;

import com.ghostchu.peerbanhelper.module.impl.rule.subscription.RuleType;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.MatchResultEnum;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 增强版规则匹配器基类
 * Enhanced rule matcher base class
 */
@Getter
public abstract class EnhancedRuleMatcher {
    protected final String ruleId;
    @Setter
    protected String ruleName;
    protected final RuleType ruleType;
    
    public EnhancedRuleMatcher(String ruleId, String ruleName, RuleType ruleType) {
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.ruleType = ruleType;
    }
    
    /**
     * 匹配逻辑
     * Matching logic
     * 
     * @param content 要匹配的内容
     * @return 匹配结果
     */
    public abstract @NotNull MatchResult match(@Nullable String content);
    
    /**
     * 更新规则数据
     * Update rule data
     * 
     * @param ruleName 规则名称
     * @param data 规则数据
     */
    public abstract void setData(String ruleName, List<?> data);
    
    /**
     * 获取规则数据大小
     * Get rule data size
     */
    public abstract int getDataSize();
    
    /**
     * 创建匹配结果
     * Create match result
     */
    protected MatchResult createMatchResult(boolean matched, @Nullable TranslationComponent comment) {
        return new MatchResult(matched ? MatchResultEnum.TRUE : MatchResultEnum.FALSE, comment);
    }
}