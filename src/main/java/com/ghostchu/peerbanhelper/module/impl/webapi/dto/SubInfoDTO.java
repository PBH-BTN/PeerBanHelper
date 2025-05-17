package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

/**
 * 订阅规则信息
 *
 * @param ruleId   规则ID
 * @param enabled  是否启用
 * @param ruleName 规则名称
 * @param subUrl   订阅地址
 */
public record SubInfoDTO(String ruleId, boolean enabled, String ruleName, String subUrl) {
}
