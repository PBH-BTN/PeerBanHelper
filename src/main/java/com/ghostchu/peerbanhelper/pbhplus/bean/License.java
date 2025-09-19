package com.ghostchu.peerbanhelper.pbhplus.bean;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;

public interface License {
    String getType();

    String getSource();

    String getLicenseTo();

    long getCreateAt();

    long getExpireAt();

    String getKeyText();

    void setKeyText(String keyText);

    long getStartAt();

    String getDescription();

    @Nullable
    String getOrderId();

    @Nullable
    String getPaymentGateway();

    @Nullable
    String getPaymentOrderId();

    @Nullable
    String getEmail();

    @Nullable
    String getSku();

    @Nullable
    BigDecimal getPaid();

    @Nullable
    List<String> getFeatures();

}
