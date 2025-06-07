package com.ghostchu.peerbanhelper.module.impl.webapi.body;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public final class LoginRequestBody {
    private String token;
}
