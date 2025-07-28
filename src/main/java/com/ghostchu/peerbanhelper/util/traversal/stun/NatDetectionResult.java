package com.ghostchu.peerbanhelper.util.traversal.stun;

import java.net.InetSocketAddress;

/**
 * NAT检测结果
 */
public record NatDetectionResult(
    NatType natType,
    InetSocketAddress localAddress,
    InetSocketAddress externalAddress,
    InetSocketAddress alternateExternalAddress,
    boolean supportsHairpin,
    String details
) {
    
    /**
     * 创建检测失败的结果
     */
    public static NatDetectionResult failed(String reason) {
        return new NatDetectionResult(
            NatType.UNKNOWN, 
            null, 
            null, 
            null, 
            false, 
            "Detection failed: " + reason
        );
    }
    
    /**
     * 检查是否为对称NAT
     */
    public boolean isSymmetric() {
        return natType == NatType.SYMMETRIC;
    }
    
    /**
     * 检查是否为锥形NAT（包括所有锥形类型）
     */
    public boolean isConeNat() {
        return natType == NatType.FULL_CONE || 
               natType == NatType.RESTRICTED_CONE || 
               natType == NatType.PORT_RESTRICTED_CONE;
    }

    /**
     * 检查是否为全锥形
     */
    public boolean isFullCone() {
        return natType == NatType.FULL_CONE;
    }
    
    /**
     * 检查是否需要NAT穿透
     */
    public boolean needsTraversal() {
        return natType != NatType.OPEN_INTERNET && natType != NatType.UNKNOWN;
    }
}
