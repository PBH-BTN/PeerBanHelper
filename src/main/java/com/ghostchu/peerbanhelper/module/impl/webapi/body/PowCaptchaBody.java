package com.ghostchu.peerbanhelper.module.impl.webapi.body;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PowCaptchaBody {
    private String captchaId;
    private String captchaNonce;
}
