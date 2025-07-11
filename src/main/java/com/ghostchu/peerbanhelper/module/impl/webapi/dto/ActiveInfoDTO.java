package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.pbhplus.ActivationKeyManager;

public record ActiveInfoDTO(
        boolean activated,
        String key,
        ActivationKeyManager.KeyData keyData,
        ActivationKeyManager.KeyData inactiveKeyData
) {

}
