package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@AllArgsConstructor
@Data
public class AutoStunConfigForm {
    private boolean enabled;
    private boolean useFriendlyLoopbackMapping;
    private List<String> downloaders;

}
