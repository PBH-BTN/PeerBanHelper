package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.module.impl.rule.subscription.RuleType;

/**
 * 增强版订阅规则信息DTO
 * Enhanced subscription rule info DTO
 *
 * @param ruleId     规则ID
 * @param enabled    是否启用
 * @param ruleName   规则名称
 * @param subUrl     订阅地址
 * @param ruleType   规则类型
 * @param description 规则描述（可选）
 */
public record EnhancedSubInfoDTO(
        String ruleId, 
        boolean enabled, 
        String ruleName, 
        String subUrl, 
        RuleType ruleType, 
        String description
) {
}