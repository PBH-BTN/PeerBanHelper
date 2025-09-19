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
public class V2License implements License {
    private transient String keyText; // 不要参与 JSON 序列化和反序列化
    // verifyMagic 应固定为 PeerBanHelper
    private String verifyMagic;
    private int licenseVersion;
    private String type; // 验证类型，afdian, mbd, 其它
    private String source; // 签发源 ALIS, Ghost_chu 等
    private String licenseTo; // 授权者可读名称
    @Nullable
    private String orderId; // 订单号
    @Nullable
    private String paymentGateway; // 支付网关类型
    @Nullable
    private String paymentOrderId; // 支付渠道订单号
    @Nullable
    private String email; // 电子邮件地址
    @Nullable
    private String sku; // 购买时的产品 SKU
    private BigDecimal paid; // 购买时支付的金额
    private long createAt;
    private long startAt;
    private long expireAt;
    private String description;
    private String hidden;
    private List<String> features;


    @Override
    public String getKeyText() {
        return keyText;
    }

    @Override
    public void setKeyText(String keyText) {
        this.keyText = keyText;
    }

}
