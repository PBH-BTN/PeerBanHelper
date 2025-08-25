package com.ghostchu.peerbanhelper.module.impl.rule.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NetworkTypeDTO {
    private boolean wideband;
    private boolean baseStation;
    private boolean governmentAndEnterpriseLine;
    private boolean businessPlatform;
    private boolean backboneNetwork;
    private boolean ipPrivateNetwork;
    private boolean internetCafe;
    private boolean iot;
    private boolean datacenter;
}
