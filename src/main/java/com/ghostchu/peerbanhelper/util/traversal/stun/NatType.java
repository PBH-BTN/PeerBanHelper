package com.ghostchu.peerbanhelper.util.traversal.stun;

/**
 * NAT类型枚举
 * 基于RFC 3489和RFC 5780的NAT类型分类
 */
public enum NatType {
    /**
     * 开放的互联网 - 没有NAT
     */
    OPEN_INTERNET("Open Internet"),
    
    /**
     * 完全锥形NAT - 一旦内部主机映射到外部端口，任何外部主机都可以通过该端口发送数据包
     */
    FULL_CONE("Full Cone NAT"),
    
    /**
     * 受限锥形NAT - 只有之前通信过的外部主机才能发送数据包
     */
    RESTRICTED_CONE("Restricted Cone NAT"),
    
    /**
     * 端口受限锥形NAT - 只有之前通信过的外部主机的特定端口才能发送数据包
     */
    PORT_RESTRICTED_CONE("Port Restricted Cone NAT"),
    
    /**
     * 对称NAT - 每个新的外部目标都会分配新的外部端口
     */
    SYMMETRIC("Symmetric NAT"),
    
    /**
     * 未知类型 - 检测失败或不支持的NAT类型
     */
    UNKNOWN("Unknown");
    
    private final String description;
    
    NatType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}
