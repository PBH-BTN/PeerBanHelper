package com.ghostchu.peerbanhelper.pbhplus.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class V1License implements License {
    // verifyMagic 应固定为 PeerBanHelper
    private String verifyMagic;
    // source 为来源
    private String source;
    // 授权给（用户名）
    private String licenseTo;
    // Key 创建时间
    private long createAt;
    // Key 过期时间，通常是 100 年以后
    private long expireAt;
    // 许可证描述
    @Nullable
    private String description;
    // 隐藏字段，主要是为了改变 KEY，PBH 并不关心这个字段
    @Nullable
    private String hidden;
    @Nullable
    private String type = "afdian"; // 默认字段

    @Override
    public long getStartAt() {
        return -1;
    }

    @Override
    public @Nullable String getOrderId() {
        return null;
    }

    @Override
    public @Nullable String getPaymentGateway() {
        return null;
    }

    @Override
    public @Nullable String getPaymentOrderId() {
        return null;
    }

    @Override
    public @Nullable String getEmail() {
        return null;
    }

    @Override
    public @Nullable String getSku() {
        return null;
    }

    @Override
    public @Nullable BigDecimal getPaid() {
        return null;
    }

    @Override
    public @Nullable List<String> getFeatures() {
        return List.of("basic", "paid");
    }
}
