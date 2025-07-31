package com.ghostchu.peerbanhelper.module.impl.rule.subscription;

/**
 * 规则类型枚举
 * Rule type enumeration for different subscription rule types
 */
public enum RuleType {
    /**
     * IP地址黑名单
     * IP address blacklist
     */
    IP_BLACKLIST("ip_blacklist"),
    
    /**
     * PeerID规则
     * PeerID rules
     */
    PEER_ID("peer_id"),
    
    /**
     * 客户端名称规则
     * Client name rules
     */
    CLIENT_NAME("client_name"),
    
    /**
     * 子串匹配规则
     * Substring matching rules
     */
    SUBSTRING_MATCH("substring_match"),
    
    /**
     * 前缀匹配规则
     * Prefix matching rules
     */
    PREFIX_MATCH("prefix_match"),
    
    /**
     * 例外列表
     * Exception/whitelist
     */
    EXCEPTION_LIST("exception_list"),
    
    /**
     * 脚本引擎规则
     * Script engine rules
     */
    SCRIPT_ENGINE("script_engine");
    
    private final String code;
    
    RuleType(String code) {
        this.code = code;
    }
    
    public String getCode() {
        return code;
    }
    
    public static RuleType fromCode(String code) {
        for (RuleType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown rule type code: " + code);
    }
    
    /**
     * 检查是否需要内存优化存储（主要用于IP规则）
     * Check if memory-optimized storage is needed (mainly for IP rules)
     */
    public boolean requiresMemoryOptimization() {
        return this == IP_BLACKLIST;
    }
}