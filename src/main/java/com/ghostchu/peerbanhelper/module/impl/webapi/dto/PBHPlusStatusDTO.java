package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PBHPlusStatusDTO {
    private Collection<String> enabledFeatures;
    private Collection<LicenseKeyPairDTO> licenses;
}
