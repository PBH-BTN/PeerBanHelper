package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.util.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.wrapper.BakedBanMetadata;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public final class BanDTO {
    private String address;
    private BakedBanMetadata banMetadata;
    private IPGeoData ipGeoData;
}
