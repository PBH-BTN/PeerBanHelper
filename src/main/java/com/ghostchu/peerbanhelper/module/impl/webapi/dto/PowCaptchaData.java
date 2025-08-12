package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PowCaptchaData {
    private String challengeId;
    private String challengeBase64;
    private int difficultyBits;
    private String algorithm;
}
